"""Backend batch contract for the existing single-event classifier."""

from ..ai_03.schemas import array, at, fail, obj
from ..serialization import camel
from .academic import validate_courses
from .preferences import classify_with_preferences, validate_user_rules


def calculate_backend(payload):
    raw = obj(payload, "$", {"now", "events", "courses", "userRules"}, {"now", "events"})
    at(raw["now"], "now")
    rules = []
    rule_fields = {"text", "label", "sleepPriority", "scope", "keywords", "courseName", "activity"}
    for i, item in enumerate(array(raw.get("userRules", []), "userRules", 500)):
        row = obj(item, f"userRules[{i}]", rule_fields, {"text", "label", "sleepPriority"})
        rules.append({{"sleepPriority": "sleep_priority", "courseName": "course_name"}.get(key, key): value
                      for key, value in row.items()})
    try:
        validate_user_rules(rules)
        validate_courses(raw.get("courses"))
    except (ValueError, TypeError) as exc:
        fail("$", str(exc))
    analyses, ids = [], set()
    event_fields = {"eventId", "text", "importance", "focus", "startAt", "deadlineAt", "courseName"}
    for i, item in enumerate(array(raw["events"], "events", 100)):
        path = f"events[{i}]"
        row = obj(item, path, event_fields, {"eventId", "text"})
        event_id = row["eventId"]
        if type(event_id) not in (int, str) or event_id == "" or event_id in ids:
            fail(path + ".eventId", "중복 없는 일정 ID가 필요합니다.")
        ids.add(event_id)
        options = {"reference_at": raw["now"], "courses": raw.get("courses"),
                   "course_name": row.get("courseName"), "importance": row.get("importance"),
                   "focus": row.get("focus"), "starts_at": row.get("startAt"),
                   "deadline_at": row.get("deadlineAt")}
        for field in ("startAt", "deadlineAt"):
            if row.get(field) is not None:
                at(row[field], path + "." + field)
        try:
            result = classify_with_preferences(row["text"], rules, options)
        except (ValueError, TypeError, OverflowError) as exc:
            fail(path, str(exc))
        analyses.append({"eventId": event_id, **camel(result)})
    return {"result": {"analyses": analyses, "warnings": [
        "RULE_BASED_SLEEP_PRIORITY_NOT_RISK_PROBABILITY", "KNOWN_ABBREVIATION_FALSE_POSITIVES",
    ]}, "modelVersion": "schedule-labeler-1.2.0"}
