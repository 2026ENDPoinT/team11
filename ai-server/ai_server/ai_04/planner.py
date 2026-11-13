"""Greedy per-night candidate search; NOT a validated fatigue/recovery predictor."""

from datetime import datetime, timedelta
from dataclasses import replace
from zoneinfo import ZoneInfo

from .schemas import clock, integer, local_datetime
from .recovery_policy import choose_extra_opportunity
from .performance_model import (prepare_history, choose_model_extra, sleep_block,
                                advance, performance, SOURCE)


def overlap(a, b, c, d):
    return a < d and c < b


def clock_distance(a, b):
    delta = abs(a.hour * 60 + a.minute - b.hour * 60 - b.minute)
    return min(delta, 1440 - delta)


def wake_anchor(day, preferred_wake, events, zone):
    anchor = datetime.combine(day, preferred_wake, zone)
    deadlines = [e["begin"] for e in events if e["event_start"].date() == day
                 and e["labels"]["movability"] != "flexible"]
    return min([anchor] + deadlines)


def search_night(events, day, lower, earliest, latest, anchor, target, desired,
                 latency, tick, preferred_bed, preferred_wake, keep_anchor):
    elapsed = max(0, int((lower - earliest).total_seconds() / 60))
    begin = earliest + timedelta(minutes=((elapsed + tick - 1) // tick) * tick)
    instants = []
    while begin <= latest:
        instants.append(begin)
        begin += timedelta(minutes=tick)
    if lower <= anchor <= latest:
        instants = sorted(set(instants + [anchor]))
    best = None
    for i, bedtime in enumerate(instants):
        for wake in instants[i + 1:]:
            if wake > anchor or (keep_anchor and wake != anchor):
                continue
            capacity = int((wake - bedtime).total_seconds() / 60) - latency
            if wake.date() != day or capacity <= 0 or capacity >= desired + tick:
                continue
            clashes = [e for e in events if overlap(bedtime, wake, e["begin"], e["finish"])]
            # Unknown stays protected until explicitly confirmed as flexible.
            if any(e["labels"]["movability"] != "flexible" for e in clashes):
                continue
            weights = {"low": 1, "medium": 2, "high": 3, "unknown": 2}
            conflict_cost = sum(weights[e["labels"]["focus_demand"]] for e in clashes)
            short = max(0, target - capacity)
            drift = clock_distance(bedtime, preferred_bed) + clock_distance(wake, preferred_wake)
            cost = (short, max(0, desired - capacity), conflict_cost, drift, bedtime, wake)
            if best is None or cost < best[0]:
                best = (cost, bedtime, wake, capacity, clashes)
    return best


def confirmation_request(events, day, best, lower, earliest, latest, target, desired,
                         latency, tick, preferred_bed, preferred_wake, zone):
    """One-event counterfactual; do not mutate or execute the hypothetical move."""
    current = best[3] if best else 0
    if current >= desired:
        return []
    options = []
    for event in events:
        if event["labels"]["movability"] != "unknown" or not overlap(
                lower, latest, event["begin"], event["finish"]):
            continue
        remaining = [e for e in events if e["id"] != event["id"]]
        anchor = wake_anchor(day, preferred_wake, remaining, zone)
        alternative = search_night(remaining, day, lower, earliest, latest, anchor,
                                   target, desired, latency, tick, preferred_bed,
                                   preferred_wake, False)
        if not alternative:
            continue
        _, bed, wake, capacity, clashes = alternative
        # Count only useful target/extra deficit reduction, not grid overshoot.
        gain = min(capacity, desired) - min(current, desired)
        if gain <= 0:
            continue
        request = {
            "event_id": event["id"], "event_title": event.get("title", ""),
            "wake_date": str(day), "reason": "UNKNOWN_EVENT_LIMITS_SLEEP_OPPORTUNITY",
            "current_sleep_capacity_minutes": current,
            "conditional_sleep_capacity_minutes": capacity,
            "potential_gain_minutes": gain,
            "conditional_bedtime": bed.isoformat(timespec="minutes"),
            "conditional_wake_time": wake.isoformat(timespec="minutes"),
            "also_requires_rescheduling_ids": [e["id"] for e in clashes],
            "question": "이 일정의 시간을 옮길 수 있나요? 가능하면 수면 기회를 더 확보할 수 있습니다.",
            "hypothetical_only": True,
        }
        options.append((gain, event["event_start"], event["id"], request))
    if not options:
        return []
    # Avoid asking about every appointment; choose the largest useful gain.
    return [min(options, key=lambda item: (-item[0], item[1], item[2]))[3]]


def recommend_recovery(summary, events, preferences, now, config, records=None):
    if preferences.get("adult_confirmed") is not True:
        raise ValueError("adult_confirmed=true required for this adult-only prototype")
    target = integer(preferences.get("target_minutes", config.default_target_minutes),
                     "target_minutes", 420, 720)
    lead = integer(preferences.get("preparation_commute_minutes", 0),
                   "preparation_commute_minutes", 0, 360)
    latency = integer(preferences.get("sleep_latency_minutes", 20),
                      "sleep_latency_minutes", 0, 120)
    preferred_bed = clock(preferences.get("preferred_bedtime", "23:00"))
    preferred_wake = clock(preferences.get("preferred_wake_time", "07:00"))
    parsed, ids = [], set()
    for event in events:
        event_id = event["id"]
        if event_id in ids:
            raise ValueError("duplicate event id")
        ids.add(event_id)
        start = local_datetime(event["start"], config.timezone)
        end = local_datetime(event["end"], config.timezone)
        if end <= start:
            raise ValueError("event end must be later than start")
        padding = integer(event.get("lead_minutes", lead), "lead_minutes", 0, 360)
        parsed.append({**event, "begin": start - timedelta(minutes=padding), "finish": end,
                       "event_start": start})
    local_now = local_datetime(now, config.timezone)
    zone = ZoneInfo(config.timezone)
    plans, previous_end = [], local_now
    history = None
    model_requested = config.recovery_policy == "model"
    policy = choose_extra_opportunity(summary, replace(config, recovery_policy="adaptive")
                                      if model_requested else config)
    if model_requested and summary["data_status"] == "complete":
        history, reason = prepare_history(summary, records or [], preferences, local_now, config)
        if history:
            policy.pop("selected_band_index", None)
            policy.pop("band_settings", None)
            policy.update(mode="model", model="UMP_2016_research_implementation",
                          additional_sleep_status="model_estimate",
                          source=SOURCE, timing_inferred_dates=history["timing_inferred_dates"],
                          warmup_days_assumed=history["warmup_days_assumed"],
                          dose_basis="published_dynamics_plus_product_reference_optimization",
                          rule_ids=["UMP_2016_EQ_1_TO_5", "PRODUCT_REFERENCE_TARGET",
                                    "PRODUCT_MAX_EXTENSION"])
        else:
            policy.update(mode="adaptive_fallback", model_unavailable_reason=reason)
    deferred = policy["additional_sleep_status"] == "deferred_insufficient_history"
    if deferred:
        policy["model_unavailable_reason"] = "INCOMPLETE_HISTORY_MODEL_UNAVAILABLE"
    extra = policy["requested_extra_minutes_per_night"] or 0
    desired = target + extra
    for offset in range(1, config.plan_days + 1):
        day = local_now.date() + timedelta(days=offset)
        model_search = None
        reference_wake_state = None
        assessment = None
        anchor = wake_anchor(day, preferred_wake, parsed, zone)
        if history:
            assessment_events = [e["event_start"] for e in parsed
                                 if e["event_start"].date() == day
                                 and e["labels"]["focus_demand"] == "high"
                                 and e["event_start"] >= anchor]
            assessment = min(assessment_events) if assessment_events else anchor + timedelta(hours=2)
            model_search = choose_model_extra(history["actual"], history["reference"],
                                              anchor, assessment, target, latency, config)
            if model_search:
                reference_wake_state = model_search.pop("reference_wake_state")
                extra = model_search["requested_extra_minutes_per_night"]
                if offset == 1:
                    policy["requested_extra_minutes_per_night"] = extra
            else:
                # Timeline cannot support an ideal reference: retain explicit fallback.
                history = None
                policy["model_timeline_fallback_from_wake_date"] = str(day)
                extra = choose_extra_opportunity(summary, replace(config, recovery_policy="adaptive"))[
                    "requested_extra_minutes_per_night"]
        desired = target + extra
        earliest = datetime.combine(day - timedelta(days=1), clock(config.earliest_bedtime), zone)
        latest = datetime.combine(day, clock(config.latest_wake_time), zone)
        lower = max(earliest, local_now, previous_end)
        tick = config.grid_minutes
        best = search_night(parsed, day, lower, earliest, latest, anchor, target, desired,
                            latency, tick, preferred_bed, preferred_wake,
                            bool(history and model_search))
        requests = confirmation_request(parsed, day, best, lower, earliest, latest,
                                        target, desired, latency, tick, preferred_bed,
                                        preferred_wake, zone)
        priority = [e["id"] for e in parsed if e["event_start"].date() == day
                    and e["labels"]["focus_demand"] == "high"]
        if best is None:
            plans.append({"wake_date": str(day), "status": "no_feasible_opportunity",
                          "bedtime": None, "wake_time": None,
                          "planned_target_shortfall_minutes": target,
                          "target_minutes": target,
                          "desired_sleep_capacity_minutes": desired,
                          "requested_additional_opportunity_minutes": None if deferred else extra,
                          "additional_sleep_status": policy["additional_sleep_status"],
                          "movability_confirmation_requests": requests,
                          "additional_opportunity_minutes": 0,
                          "unmet_additional_opportunity_minutes": None if deferred else extra,
                          "grid_rounding_extra_minutes": 0,
                          "priority_event_ids": priority, "requires_rescheduling_ids": [],
                          "rule_ids": ["PRODUCT_FIXED_EVENT_BLOCK", "PRODUCT_NIGHT_WINDOW"]})
            if history:
                # No future sleep is assumed when no interval can be scheduled.
                history["actual"] = advance(history["actual"], latest)
                if reference_wake_state:
                    history["reference"] = reference_wake_state
            continue
        _, bed, wake, capacity, clashes = best
        plans.append({"wake_date": str(day),
                      "status": "requires_rescheduling" if clashes else
                      "target_met" if capacity >= target else "target_shortfall",
                      "bedtime": bed.isoformat(timespec="minutes"),
                      "wake_time": wake.isoformat(timespec="minutes"),
                      "time_in_bed_minutes": int((wake - bed).total_seconds() / 60),
                      "assumed_sleep_latency_minutes": latency,
                      "planned_sleep_capacity_minutes": capacity,
                      "target_minutes": target,
                      "desired_sleep_capacity_minutes": desired,
                      "requested_additional_opportunity_minutes": None if deferred else extra,
                      "additional_sleep_status": policy["additional_sleep_status"],
                      "movability_confirmation_requests": requests,
                      "planned_target_shortfall_minutes": max(0, target - capacity),
                      "additional_opportunity_minutes": max(0, capacity - target),
                      "unmet_additional_opportunity_minutes": None if deferred else max(0, extra - max(0, capacity - target)),
                      "grid_rounding_extra_minutes": max(0, capacity - desired),
                      "priority_event_ids": priority,
                      "requires_rescheduling_ids": [e["id"] for e in clashes],
                      "rule_ids": ["E_HISTORY", "E_NO_LINEAR_REPAYMENT",
                                   "PRODUCT_TARGET_FIRST", "PRODUCT_FLEXIBLE_EVENT_COST",
                                   "PRODUCT_PREFERENCE_TIEBREAK", "PRODUCT_SHORTFALL_BANDS"]})
        previous_end = wake
        if history and model_search:
            actual_wake = sleep_block(history["actual"], bed + timedelta(minutes=latency), wake)
            # A candidate may wake later than the originally selected assessment time.
            if assessment < wake:
                actual_prediction = None
                residual_gap = None
            else:
                actual_prediction = performance(advance(actual_wake, assessment))
                residual_gap = actual_prediction - model_search["reference_performance_index"]
            plans[-1]["performance_model"] = {
                "search": model_search,
                "actual_plan_performance_index": round(actual_prediction, 6) if actual_prediction is not None else None,
                "actual_plan_gap_from_reference": round(residual_gap, 6) if residual_gap is not None else None,
                "reference_reached_by_actual_plan": residual_gap <= 1e-9 if residual_gap is not None else False,
                "interpretation": "집단 PVT 모형 지수의 비교이며 실제 피로·신체 회복 완료 판정이 아닙니다."}
            plans[-1]["rule_ids"] = ["UMP_2016_EQ_1_TO_5", "PRODUCT_REFERENCE_TARGET",
                                      "PRODUCT_TARGET_FIRST", "PRODUCT_MAX_EXTENSION"]
            history["actual"], history["reference"] = actual_wake, reference_wake_state
        preferred_bed, preferred_wake = bed.time(), wake.time()
    if deferred:
        for plan in plans:
            plan["rule_ids"] = [rule for rule in plan["rule_ids"]
                                if rule != "PRODUCT_SHORTFALL_BANDS"] + ["PRODUCT_HISTORY_REQUIRED_FOR_EXTRA"]
    return {"method": "greedy_nightly_grid_search", "plans": plans,
            "movability_confirmation_requests": [request for plan in plans
                                                  for request in plan["movability_confirmation_requests"]],
            "extra_opportunity_policy_minutes": policy["requested_extra_minutes_per_night"],
            "policy": policy,
            "recovery_completion": None,
            "interpretation": "수면 기회를 배치한 계획이며 실제 수면량·회복 완료 예측이 아닙니다."}
