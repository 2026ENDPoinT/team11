"""Map the complete Spring snapshot to the unchanged planner input contract."""

from copy import deepcopy

from ..errors import APIError
from ..limits import validate_limits
from .Sleep_plan.planner import plan_dates, read_profile, schedule

CONTEXT = ("now", "targetDate", "firstEventStartAt", "eveningEvents",
           "examNextDay", "avgWakeTime", "recentSleep")


def prepare_request(raw):
    if not isinstance(raw, dict):
        raise APIError("INVALID_INPUT", "요청은 JSON 객체여야 합니다.")
    validate_limits(raw, {"sleepHistory": 500, "recentSleep": 500, "timetable": 500,
                          "events": 500, "eveningEvents": 500})
    for field in ("now", "targetDate", "profile", "sleepHistory", "timetable", "events"):
        if field not in raw:
            raise APIError("INVALID_INPUT", "필수 스냅샷 필드가 없습니다.", field=field)
    if not isinstance(raw["profile"], dict):
        raise APIError("INVALID_INPUT", "profile은 객체여야 합니다.", field="profile")
    payload = {key: deepcopy(raw[key]) for key in CONTEXT if key in raw}
    payload["profile"] = deepcopy(raw["profile"])
    for field in ("sleepHistory", "timetable", "events", "recentSleep", "eveningEvents"):
        if field not in raw:
            continue
        items = raw[field]
        if not isinstance(items, list) or any(not isinstance(item, dict) for item in items):
            raise APIError("INVALID_INPUT", "전체 페이지를 합친 객체 배열이 필요합니다.", field=field)
        payload[field] = deepcopy(items)
    try:
        _, target = plan_dates(payload)
        read_profile(payload["profile"])
        schedule(payload, target)
    except (ValueError, KeyError, TypeError, OverflowError) as error:
        raise APIError("INVALID_INPUT", f"계산 입력 오류: {error}") from None
    # The HTTP endpoint is now ordinary-day planning. Keep exam events as
    # fixed blockers, but leave exam-specific sleep targets to AI-03.
    payload["examNextDay"] = False
    for field in ("events", "eveningEvents"):
        for item in payload.get(field, []):
            if item.get("type") == "EXAM":
                item["type"] = "PERSONAL"
    return payload
