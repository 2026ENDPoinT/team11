"""Single integration entry point. No DB, web framework or global clock dependency."""

from .labeler import classify_event
from .planner import recommend_recovery
from .schemas import Config, local_datetime, parse_date
from .shortfall import calculate_shortfall


def analyze(payload, classifier=None, config=None):
    if payload.get("schema_version", "0.1") != "0.1":
        raise ValueError("unsupported schema_version")
    cfg = Config.from_dict(config)
    now = local_datetime(payload["now"], cfg.timezone)
    as_of = parse_date(payload["as_of_date"])
    if as_of > now.date():
        raise ValueError("as_of_date cannot be in the future")
    records = payload.get("sleep_records", [])
    for record in records:
        if record.get("sleep_end") and local_datetime(record["sleep_end"], cfg.timezone) > now:
            raise ValueError("actual sleep cannot end in the future")
    summary = calculate_shortfall(records, str(as_of), cfg)
    events = [{**event, "labels": classify_event(event, classifier)}
              for event in payload.get("events", [])]
    plan = recommend_recovery(summary, events, payload["preferences"], payload["now"], cfg,
                              records=records)
    warnings = ["NOT_A_PHYSIOLOGICAL_DEBT_MODEL", "CALENDAR_COVERAGE_NOT_VERIFIED"]
    policy = plan["policy"]
    if policy["mode"] == "model":
        warnings.extend(["GROUP_PVT_MODEL_NOT_PERSONALIZED", "ASSUMED_PREHISTORY",
                         "NOT_A_WHOLE_BODY_RECOVERY_MODEL"])
        if policy.get("timing_inferred_dates"):
            warnings.append("HISTORICAL_SLEEP_TIMING_INFERRED")
    elif policy["mode"] == "adaptive_fallback":
        warnings.append("MODEL_UNAVAILABLE_USING_PRODUCT_BANDS")
    elif policy["mode"] == "baseline_only":
        warnings.append("ADDITIONAL_SLEEP_DEFERRED_INCOMPLETE_HISTORY")
    if plan["movability_confirmation_requests"]:
        warnings.append("EVENT_MOVABILITY_CONFIRMATION_AVAILABLE")
    if summary["missing_dates"]:
        warnings.append("INCOMPLETE_SLEEP_RECORDS")
    if any(row["target_source"] == "config_default" for row in summary["daily"]):
        warnings.append("HISTORICAL_TARGET_DEFAULT_USED")
    if any(e["labels"]["source"] != "llm" for e in events):
        warnings.append("SOME_LABELS_ARE_NOT_LLM_OUTPUT")
    return {"schema_version": "0.1", "rule_version": cfg.rule_version,
            "as_of_date": str(as_of), "sleep_summary": summary,
            "events": events, "recovery_plan": plan, "warnings": warnings}
