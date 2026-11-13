"""Compute the three sleep-plan values from a local snapshot."""

import math
from dataclasses import dataclass
from datetime import UTC, date, datetime, time, timedelta
from itertools import pairwise

from ..Fatigue.inputs import KST

from .history import RECENT, circular_mean, clean_history, clock_minute, fatigue_from_history, minutes, recent_timeline, stamp

STEP = timedelta(minutes=15)
WEEKDAYS = ("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
BASE_WEIGHTS = (0.40, 0.35, 0.15, 0.10)
EPSILON = 0.02


@dataclass(frozen=True)
class Profile:
    need: int
    latency: int
    minimum: int
    departure_buffer: int


@dataclass(frozen=True)
class Activity:
    start: datetime
    end: datetime
    importance: int


@dataclass(frozen=True)
class Candidate:
    bed: datetime
    wake: datetime
    sleep: int

    def raw(self):
        return {
            "recommendedBedAt": self.bed.astimezone(KST).isoformat(),
            "recommendedWakeAt": self.wake.astimezone(KST).isoformat(),
            "sleepMin": self.sleep,
        }


def local_at(day, at=time()):
    return datetime.combine(day, at, KST).astimezone(UTC)


def read_clock(value):
    if not isinstance(value, str) or len(value) != 5 or value[2] != ":":
        raise ValueError("시계 시각은 HH:MM 형식이어야 합니다.")
    return time(int(value[:2]), int(value[3:]))


def plan_dates(payload):
    now = stamp(payload["now"]) if "now" in payload else datetime.now(UTC)
    local_now = now.astimezone(KST)
    target = date.fromisoformat(payload["targetDate"]) if "targetDate" in payload else (
        local_now.date() + timedelta(days=int(local_now.hour >= 6))
    )
    if target not in {local_now.date(), local_now.date() + timedelta(days=1)}:
        raise ValueError("targetDate는 한국 시간 기준 오늘 또는 내일의 기상일이어야 합니다.")
    return now, target


def read_profile(values):
    def whole(name, low, high, default=None):
        value = values.get(name, default)
        if type(value) is not int or not low <= value <= high:
            raise ValueError(f"profile.{name}은 {low}~{high} 범위의 정수여야 합니다.")
        return value

    need = whole("sleepNeedMin", 300, 600)
    minimum = whole("minSleepMin", 240, 480, 300)
    if minimum > need:
        raise ValueError("minSleepMin은 sleepNeedMin 이하여야 합니다.")
    prep_key = "prepMinEstimate" if values.get("prepMinEstimate") is not None else "prepMin"
    prep = whole(prep_key, 0, 360 if prep_key == "prepMinEstimate" else 240)
    return Profile(
        need, whole("sleepLatencyMin", 0, 60), minimum,
        prep + whole("commuteMin", 0, 240) + whole("bufferMin", 0, 60),
    )


def rows(payload, key):
    value = payload.get(key, [])
    if not isinstance(value, list) or any(not isinstance(item, dict) for item in value):
        raise ValueError(f"{key}는 객체 배열이어야 합니다.")
    return value


def schedule(payload, target):
    blocks = set()
    activities = set()
    prior = target - timedelta(days=1)
    exam = payload.get("examNextDay", False)
    if type(exam) is not bool:
        raise ValueError("examNextDay는 불리언이어야 합니다.")
    for row in rows(payload, "timetable"):
        weekday = row["dayOfWeek"]
        start, end = read_clock(row["startTime"]), read_clock(row["endTime"])
        if weekday not in WEEKDAYS or start >= end:
            raise ValueError("수업 요일 또는 시작·종료 시각이 올바르지 않습니다.")
        for day in (prior, target):
            if WEEKDAYS[day.weekday()] == weekday:
                block = Activity(local_at(day, start), local_at(day, end), 1)
                blocks.add(block)
                if day == target:
                    activities.add(block)
    for row in rows(payload, "events") + rows(payload, "eveningEvents"):
        all_day = row.get("allDay", False)
        if type(all_day) is not bool:
            raise ValueError("allDay는 불리언이어야 합니다.")
        start = stamp(row["startAt"])
        on_target = start.astimezone(KST).date() == target
        is_exam = row.get("type") == "EXAM"
        exam |= is_exam and on_target
        if all_day:
            continue
        end = stamp(row["endAt"]) if row.get("endAt") is not None else start
        if end < start:
            raise ValueError("일정 종료 시각이 시작 시각보다 빠릅니다.")
        block = Activity(start, end, 2 if is_exam else 1)
        blocks.add(block)
        if on_target:
            activities.add(block)
    if payload.get("firstEventStartAt") is not None:
        first = stamp(payload["firstEventStartAt"])
        if first.astimezone(KST).date() != target:
            raise ValueError("firstEventStartAt의 한국 시간 날짜는 targetDate와 같아야 합니다.")
        if all(activity.start != first for activity in activities):
            point = Activity(first, first, 1)
            activities.add(point)
            blocks.add(point)
    return blocks, activities, exam


def merged_activities(activities):
    """Avoid double-counting overlapping activities by taking the highest importance."""
    spans = [item for item in activities if item.start < item.end]
    boundaries = sorted({at for item in spans for at in (item.start, item.end)})
    merged = []
    for left, right in pairwise(boundaries):
        importance = max((item.importance for item in spans if item.start <= left < item.end), default=0)
        if importance:
            if merged and merged[-1].end == left and merged[-1].importance == importance:
                merged[-1] = Activity(merged[-1].start, right, importance)
            else:
                merged.append(Activity(left, right, importance))
    points = {}
    for item in activities:
        if item.start == item.end and not any(
            span.start <= item.start < span.end and span.importance >= item.importance for span in spans
        ):
            points[item.start] = max(item.importance, points.get(item.start, 0))
    return merged + [Activity(at, at, importance) for at, importance in sorted(points.items())]


def circular_distance(first, second):
    return abs((first - second + 720) % 1440 - 720)


def floor_grid(value, anchor):
    return anchor + ((value - anchor) // STEP) * STEP


def generate_candidates(now, target, profile, blocks, wake_deadline, preferred):
    anchor = local_at(target)
    earliest = max(now, local_at(target - timedelta(days=1), time(18)))
    latest = min(wake_deadline, anchor + timedelta(days=1) - STEP)
    wake = floor_grid(preferred, anchor) if preferred is not None else anchor
    candidates = []
    while wake <= latest:
        # Every blocker before wake must end by bedtime. A point blocks its own minute.
        ends = [
            item.end if item.start < item.end else item.end + timedelta(microseconds=1)
            for item in blocks if item.start < wake and item.end >= earliest
        ]
        earliest_bed = max([earliest, *ends])
        bed = floor_grid(wake - timedelta(minutes=profile.minimum + profile.latency), anchor)
        while bed >= earliest_bed:
            duration = int(minutes(wake - bed)) - profile.latency
            if duration > 600:
                break
            candidates.append(Candidate(bed, wake, duration))
            bed -= STEP
        wake += STEP
    enough = [item for item in candidates if item.sleep >= 420]
    feasible = enough or candidates
    if preferred is not None and feasible:
        first_wake = min(item.wake for item in feasible)
        feasible = [item for item in feasible if item.wake == first_wake]
    return feasible


def evolve(value, duration_hours, awake, tau_w, tau_s):
    if awake:
        return 1 - (1 - value) * math.exp(-duration_hours / tau_w)
    return value * math.exp(-duration_hours / tau_s)


def history_pressure(timeline, initial, tau_w, tau_s):
    value = initial
    for part in timeline:
        if part.state != "unknown":
            value = evolve(value, minutes(part.end - part.start) / 60, part.state == "awake", tau_w, tau_s)
    return value


def schedule_decay(wake, activities, tau_w):
    if not activities:
        return math.exp(-2 / tau_w)
    total = 0
    for item in activities:
        start = minutes(item.start - wake) / 60
        length = minutes(item.end - item.start) / (60 * tau_w)
        mean = -math.expm1(-length) / length if length else 1
        total += item.importance * math.exp(-start / tau_w) * mean
    return total / sum(item.importance for item in activities)


def score_candidates(pool, profile, now, activities, habits, pressure, fatigue, debt_ratio, taus, weights):
    habit_bed, habit_middle = habits
    tau_w, tau_s = taus
    decay = {}
    result = []
    for candidate in pool:
        if candidate.wake not in decay:
            decay[candidate.wake] = schedule_decay(candidate.wake, activities, tau_w)
        onset = candidate.bed + timedelta(minutes=profile.latency)
        at_onset = evolve(pressure, minutes(onset - now) / 60, True, tau_w, tau_s)
        at_wake = evolve(at_onset, candidate.sleep / 60, False, tau_w, tau_s)
        future = 1 - (1 - at_wake) * decay[candidate.wake]
        short = max(0, profile.need - candidate.sleep) / profile.need
        midpoint = clock_minute(onset + (candidate.wake - onset) / 2)
        rhythm = (1 - math.cos(math.tau * (midpoint - habit_middle) / 1440)) / 2 if habit_middle is not None else 0
        shift = min(1, circular_distance(clock_minute(candidate.bed), habit_bed) / 180) if habit_bed is not None else 0
        components = (future, (1 + 0.3 * fatigue * debt_ratio) * short, rhythm, shift)
        cost = sum(weight * value for weight, value in zip(weights, components, strict=True))
        result.append((candidate, cost, rhythm, shift))
    return result


def choose(scored, goal):
    best = min(row[1] for row in scored)
    return min(
        (row for row in scored if row[1] <= best + EPSILON),
        key=lambda row: (abs(goal - row[0].sleep), row[2] + row[3], row[1], -row[0].wake.timestamp()),
    )[0]


def optimize(pool, feasible, profile, now, activities, habits, timeline, fatigue, debt_ratio, goal):
    taus = (18.2, 4.2)
    cases = [
        (0.5, taus, BASE_WEIGHTS), (0.2, taus, BASE_WEIGHTS), (0.8, taus, BASE_WEIGHTS),
        (0.5, (14.56, 5.04), BASE_WEIGHTS), (0.5, (21.84, 3.36), BASE_WEIGHTS),
        (0.5, taus, (0.48, 0.35, 0.10, 0.07)), (0.5, taus, (0.32, 0.35, 0.20, 0.13)),
    ]
    def pick(initial, constants, weights, fatigue_weight, *, candidates=pool, rhythm=habits):
        pressure = history_pressure(timeline, initial, *constants)
        scored = score_candidates(
            candidates, profile, now, activities, rhythm, pressure, fatigue_weight, debt_ratio, constants, weights,
        )
        return choose(scored, goal)

    plans = [pick(initial, constants, weights, fatigue) for initial, constants, weights in cases]
    spread = max(
        minutes(max(item.bed for item in plans) - min(item.bed for item in plans)),
        minutes(max(item.wake for item in plans) - min(item.wake for item in plans)),
        max(item.sleep for item in plans) - min(item.sleep for item in plans),
    )
    return plans[0], spread, {
        "pressure": pick(0.5, taus, (0.40 / 0.75, 0.35 / 0.75, 0, 0), 0,
                         candidates=feasible, rhythm=(None, None)).raw(),
        "pressureAndRhythm": pick(0.5, taus, BASE_WEIGHTS, 0).raw(),
        "withFatigue": plans[0].raw(),
    }


def analyze_plan(payload, *, history_unavailable=False):
    """Return raw plan values plus internal diagnostics for the caller."""
    if not isinstance(payload, dict) or not isinstance(payload.get("profile"), dict):
        raise ValueError("profile을 포함한 JSON 객체가 필요합니다.")
    if "timetable" not in payload or "events" not in payload:
        raise ValueError("시간표와 일정 목록을 전달해야 합니다. 일정이 없으면 빈 배열을 사용하세요.")
    now, target = plan_dates(payload)
    profile = read_profile(payload["profile"])
    blocks, activities, exam = schedule(payload, target)
    first = min((item.start for item in activities), default=None)
    goal = min(600, profile.need + (30 if exam else 0))
    recent_snapshot = [{"status": "CLOSED", **row} for row in rows(payload, "recentSleep")]
    sleep_rows = rows(payload, "sleepHistory") + recent_snapshot
    nights, invalid = clean_history(sleep_rows, now, profile.latency)
    recent = [night for night in nights if night.wake >= now - RECENT]
    preferred = None
    if first is None:
        preferred_clock = payload.get("avgWakeTime")
        if preferred_clock is not None:
            preferred = local_at(target, read_clock(preferred_clock))
        else:
            average = circular_mean(clock_minute(night.wake) for night in nights
                                    if night.wake >= now - timedelta(days=14))
            preferred = local_at(target, time(9)) if average is None else (
                local_at(target) + timedelta(minutes=round(average) % 1440)
            )
    wake_deadline = first - timedelta(minutes=profile.departure_buffer) if first is not None else local_at(
        target + timedelta(days=1)
    )
    feasible = generate_candidates(now, target, profile, blocks, wake_deadline, preferred)
    if not feasible:
        return {"plan": None, "mode": "UNAVAILABLE", "warnings": ["NO_FEASIBLE_PLAN"]}
    if exam:
        feasible = [item for item in feasible if item.sleep >= goal] or feasible
    rule = min(feasible, key=lambda item: (abs(item.sleep - goal), -item.wake.timestamp()))

    per_day = {}
    for night in recent:
        per_day[night.day] = per_day.get(night.day, 0) + night.sleep_min
    debt = sum(max(0, profile.need - value) for value in per_day.values())
    timeline = recent_timeline(recent, now)
    long_gap = any(part.state == "unknown" and part.end - part.start > timedelta(hours=30)
                   for part in timeline if part.start != now - RECENT)
    stale = bool(recent and now - recent[-1].wake > timedelta(hours=24))
    warnings = ["AWAKENINGS_NOT_ESTIMATED"]
    if history_unavailable:
        warnings.append("SLEEP_LOG_LOOKUP_FAILED")
    if invalid:
        warnings.append("INVALID_OR_OVERLAPPING_LOGS")
    if long_gap or stale:
        warnings.append("UNOBSERVED_SLEEP_WAKE_GAP")
    if len(per_day) < 5:
        warnings.append("INSUFFICIENT_HISTORY")

    plan, mode = rule, "RULE"
    diagnostics = {
        "observedNights": len(per_day), "sleepDeficitMin": round(debt, 2),
        "fatigueScore": None, "sensitivitySpreadMin": None, "ablation": {"rule": rule.raw()},
        "currentPressure": None,
        "unknownHistoryMin": round(sum(minutes(part.end - part.start) for part in timeline
                                       if part.state == "unknown"), 2),
        "wakeDeadline": wake_deadline.astimezone(KST).isoformat() if first is not None else None,
    }
    if not (history_unavailable or invalid or long_gap or stale or len(per_day) < 5):
        main_by_day = {}
        for night in recent:
            if night.day not in main_by_day or night.sleep_min > main_by_day[night.day].sleep_min:
                main_by_day[night.day] = night
        habit_bed = circular_mean(clock_minute(night.bed) for night in main_by_day.values())
        habit_middle = circular_mean(clock_minute(night.midpoint) for night in main_by_day.values())
        pool = [item for item in feasible if habit_bed is None or circular_distance(
            clock_minute(item.bed), habit_bed
        ) <= 180]
        if not pool:
            warnings.append("HABIT_WINDOW_RELAXED")
        fatigue, coverage = fatigue_from_history(nights, now)
        score = fatigue["fatigueScore"]
        if score is None:
            warnings.append("FATIGUE_UNAVAILABLE")
        elif fatigue["status"] == "partial":
            warnings.append("FATIGUE_PARTIAL")
        normalized = 0 if score is None else (max(1, score) - 1) / 99
        proposed, spread, ablation = optimize(
            pool or feasible, feasible, profile, now, merged_activities(activities), (habit_bed, habit_middle),
            timeline, normalized, min(1, debt / (3 * profile.need)), goal,
        )
        diagnostics.update(
            fatigueScore=score, fatigueCoverage=coverage, sensitivitySpreadMin=round(spread, 2),
            currentPressure=round(history_pressure(timeline, 0.5, 18.2, 4.2), 6),
        )
        diagnostics["ablation"].update(ablation)
        if spread >= 60:
            warnings.append("SENSITIVITY_FALLBACK")
        else:
            plan, mode = proposed, "OPTIMIZED"
    if preferred is not None and plan.wake > floor_grid(preferred, local_at(target)):
        warnings.append("FREE_WAKE_ADJUSTED")
    if plan.sleep < 420:
        warnings.append("BELOW_7_HOURS")
    if plan.sleep < goal:
        warnings.append("SLEEP_GOAL_NOT_MET")
    reasons = ["FIRST_EVENT_DEADLINE" if first is not None else "FREE_WAKE_REFERENCE"]
    reasons.append("NEAR_MINIMUM_COST" if mode == "OPTIMIZED" else "RULE_TARGET")
    return {"plan": plan.raw(), "mode": mode, "reasons": reasons, "warnings": warnings, **diagnostics}
