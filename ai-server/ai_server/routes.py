"""Register each endpoint's request-to-response service here."""

from .ai_02.service import calculate
from .ai_03.service import calculate as calculate_memory_sleep
from .ai_03.backend import BACKEND_CONTRACT, calculate_backend
from .ai_03.spring_snapshot import SPRING_CONTRACT, calculate_spring_snapshot
from .ai_04.service import analyze
from .ai_04.adapters import calculate_backend as calculate_recovery_backend
from .ai_05.preferences import classify_with_preferences
from .ai_05.backend import calculate_backend as calculate_risks_backend
from .ai_06.service import calculate as calculate_focus_sleep
from .errors import APIError

EXAM_SLEEP_ROUTE = "/ai/v1/plan/exam-sleep"
RECOVERY_ROUTE = "/ai/v1/plan/sleep-recovery"
SCHEDULE_LABEL_ROUTE = "/ai/v1/schedule/label"
RECOVERY_BACKEND_ROUTE = "/ai/v1/sleep-debt/calculate"
SCHEDULE_BACKEND_ROUTE = "/ai/v1/insights/risks"
FOCUS_SLEEP_ROUTE = "/ai/v1/plan/focus-sleep"


def calculate_recovery(payload):
    if not isinstance(payload, dict) or not isinstance(payload.get("preferences"), dict):
        raise APIError("INVALID_INPUT", "JSON 객체와 preferences 객체가 필요합니다.")
    for key in ("sleep_records", "events"):
        rows = payload.get(key, [])
        if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
            raise APIError("INVALID_INPUT", f"{key}는 객체 배열이어야 합니다.", field=key)
    try:
        result = analyze(payload)
    except (ValueError, KeyError, TypeError, OverflowError) as exc:
        raise APIError("INVALID_INPUT", str(exc)) from None
    return {"result": result, "modelVersion": result["rule_version"]}


def calculate_schedule_label(payload):
    if not isinstance(payload, dict):
        raise APIError("INVALID_INPUT", "JSON 객체가 필요합니다.")
    rules = payload.get("user_rules", [])
    if not isinstance(rules, list):
        raise APIError("INVALID_INPUT", "user_rules는 배열이어야 합니다.", field="user_rules")
    try:
        result = classify_with_preferences(payload.get("text"), rules, payload.get("options"))
    except (ValueError, TypeError, OverflowError) as exc:
        raise APIError("INVALID_INPUT", str(exc)) from None
    return {"result": result, "modelVersion": result["rule_version"]}


def calculate_exam_sleep(payload):
    if isinstance(payload, dict):
        if payload.get("contractVersion") == SPRING_CONTRACT:
            return calculate_spring_snapshot(payload)
        if payload.get("contractVersion") == BACKEND_CONTRACT:
            return calculate_backend(payload)
        if "contractVersion" not in payload and ("exam" in payload or "mode" in payload):
            raise APIError("RETIRED_CONTRACT", "이전 시험 추천 요청 형식은 폐기되었습니다. 현재 AI-03 계약으로 요청하세요.", 410)
    return calculate_memory_sleep(payload)


POST_ROUTES = {
    "/ai/v1/plan/tonight": calculate,
    EXAM_SLEEP_ROUTE: calculate_exam_sleep,
    RECOVERY_ROUTE: calculate_recovery,
    SCHEDULE_LABEL_ROUTE: calculate_schedule_label,
    RECOVERY_BACKEND_ROUTE: calculate_recovery_backend,
    SCHEDULE_BACKEND_ROUTE: calculate_risks_backend,
    FOCUS_SLEEP_ROUTE: calculate_focus_sleep,
}
