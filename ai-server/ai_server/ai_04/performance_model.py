"""Research implementation of UMP 2016 equations, not a clinical recovery model.

Source: Ramakrishnan et al., SLEEP 39(1):249-262, Table 2 and Results.
https://doi.org/10.5665/sleep.5358
The published group parameters are retained. History reconstruction, reference
schedule, warm-up and the planning objective are explicit product assumptions.
"""

from dataclasses import dataclass
from datetime import datetime, timedelta
import math
from zoneinfo import ZoneInfo

from .schemas import clock, local_datetime, parse_date

SOURCE = "https://doi.org/10.5665/sleep.5358"
U = 18.35
TAU_WAKE_HOURS = 40.0
TAU_SLEEP_HOURS = 2.11
TAU_DEBT_HOURS = 7.0 * 24
DEBT_FLOOR = -0.11
CIRCADIAN_AMPLITUDE = 3.26
PHASE_HOURS = 2.31
HARMONICS = (0.97, 0.22, 0.07, 0.03, 0.001)


@dataclass(frozen=True)
class State:
    at: datetime
    pressure: float = 0.5
    debt: float = 0.0  # dimensionless model state, NOT hours owed


def evolve(state, hours, asleep):
    """Exact solution of the piecewise linear ODEs, including debt floor."""
    if not math.isfinite(hours) or hours < 0:
        raise ValueError("model duration must be finite and nonnegative")
    if hours == 0:
        return state
    s, d = state.pressure, max(DEBT_FLOOR, state.debt)
    if not asleep:
        sn = U + (s - U) * math.exp(-hours / TAU_WAKE_HOURS)
        dn = 1 + (d - 1) * math.exp(-hours / TAU_DEBT_HOURS)
    else:
        crossing = (-TAU_DEBT_HOURS * math.log((DEBT_FLOOR + 2) / (d + 2))) if d > DEBT_FLOOR else 0
        first = min(hours, crossing)
        es = math.exp(-first / TAU_SLEEP_HOURS)
        ed = math.exp(-first / TAU_DEBT_HOURS)
        sn = -2 * U + (s + 2 * U) * es + U * (d + 2) * TAU_DEBT_HOURS / (
            TAU_DEBT_HOURS - TAU_SLEEP_HOURS) * (ed - es)
        dn = -2 + (d + 2) * ed
        if hours > first:
            sn = U * DEBT_FLOOR + (sn - U * DEBT_FLOOR) * math.exp(
                -(hours - first) / TAU_SLEEP_HOURS)
            dn = DEBT_FLOOR
    return State(state.at + timedelta(hours=hours), sn, max(DEBT_FLOOR, dn))


def advance(state, until, asleep=False):
    return evolve(state, (until - state.at).total_seconds() / 3600, asleep)


def performance(state):
    hours = state.at.hour + state.at.minute / 60 + state.at.second / 3600
    c = sum(a * math.sin(i * 2 * math.pi * (hours + PHASE_HOURS) / 24)
            for i, a in enumerate(HARMONICS, start=1))
    return state.pressure + CIRCADIAN_AMPLITUDE * c


def sleep_block(state, sleep_start, wake):
    return advance(advance(state, sleep_start), wake, asleep=True)


def prepare_history(summary, records, preferences, now, config):
    """Prefer actual intervals; infer missing timing explicitly, never missing days."""
    if summary["data_status"] != "complete":
        return None, "INCOMPLETE_HISTORY_MODEL_UNAVAILABLE"
    if summary["nap_minutes"]:
        return None, "NAP_TIMING_REQUIRED_FOR_MODEL"
    if parse_date(summary["window_end"]) != now.date():
        return None, "STALE_HISTORY_MODEL_UNAVAILABLE"
    zone = ZoneInfo(config.timezone)
    preferred_wake = clock(preferences.get("preferred_wake_time", "07:00"))
    target = preferences.get("target_minutes", config.default_target_minutes)
    first_day = parse_date(summary["window_start"])
    # Product assumption: unknown earlier history is a regular target-length schedule.
    start_day = first_day - timedelta(days=config.model_warmup_days + 1)
    start = datetime.combine(start_day, preferred_wake, zone)
    actual = reference = State(start)
    for i in range(1, config.model_warmup_days + 1):
        wake = datetime.combine(start_day + timedelta(days=i), preferred_wake, zone)
        actual = sleep_block(actual, wake - timedelta(minutes=target), wake)
    reference = actual
    raw = {r["date"]: r for r in records}
    inferred = []
    for row in summary["daily"]:
        day = parse_date(row["date"])
        wake_reference = datetime.combine(day, preferred_wake, zone)
        reference = sleep_block(reference, wake_reference - timedelta(minutes=target), wake_reference)
        record = raw.get(row["date"], {})
        wake = local_datetime(record["sleep_end"], config.timezone) if record.get("sleep_end") else wake_reference
        if not record.get("sleep_end") or record.get("awake_minutes", 0):
            inferred.append(row["date"])
        begin = wake - timedelta(minutes=row["actual_night_sleep_minutes"])
        if wake > now or begin < actual.at:
            return None, "HISTORY_TIMING_INCONSISTENT_MODEL_UNAVAILABLE"
        actual = sleep_block(actual, begin, wake)
    if reference.at > now:
        return None, "REFERENCE_WAKE_NOT_YET_OBSERVED"
    return {"actual": advance(actual, now), "reference": advance(reference, now),
            "timing_inferred_dates": inferred,
            "warmup_days_assumed": config.model_warmup_days}, None


def choose_model_extra(actual, reference, wake, assessment, target, latency, config):
    """Smallest extension reaching reference; otherwise best available model value."""
    reference_bed = wake - timedelta(minutes=target + latency)
    if reference_bed < reference.at:
        return None
    ref_wake = sleep_block(reference, wake - timedelta(minutes=target), wake)
    ref_value = performance(advance(ref_wake, assessment))
    step = config.grid_minutes
    extras = sorted(set(range(0, config.max_extra_opportunity_minutes + 1, step)) |
                    {config.max_extra_opportunity_minutes})
    candidates = []
    for extra in extras:
        bed = reference_bed - timedelta(minutes=extra)
        if bed < actual.at:
            continue
        future_wake = sleep_block(actual, bed + timedelta(minutes=latency), wake)
        value = performance(advance(future_wake, assessment))
        candidates.append({"extra_minutes": extra, "predicted_performance_index": value,
                           "gap_from_reference": value - ref_value})
    if not candidates:
        return None
    meets = [c for c in candidates if c["gap_from_reference"] <= 1e-9]
    chosen = min(meets, key=lambda c: c["extra_minutes"]) if meets else min(
        candidates, key=lambda c: (c["predicted_performance_index"], c["extra_minutes"]))
    return {"requested_extra_minutes_per_night": chosen["extra_minutes"],
            "reference_reached_in_search": bool(meets),
            "assessment_time": assessment.isoformat(timespec="minutes"),
            "reference_performance_index": ref_value,
            "candidates": [{k: round(v, 6) if isinstance(v, float) else v
                            for k, v in c.items()} for c in candidates],
            "reference_wake_state": ref_wake}
