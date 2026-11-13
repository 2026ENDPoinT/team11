"""Internal API adapter for the standalone focus-mode planner."""

from datetime import date, timedelta
import re

from ..ai_03.schemas import array, at, enum, fail, number, obj
from ..ai_03.intervals import KST
from ..errors import APIError

CONTRACT = "FOCUS_SLEEP_V1"
MODEL_VERSION = "focus-sleep-pvt-1.0.0"
CLOCK = re.compile(r"(?:[01][0-9]|2[0-3]):[0-5][0-9]")
DAY = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}")


def history_record(value, index):
    path = f"history[{index}]"
    required = {"wakeDate", "bedtime", "wakeTime", "asleepMinutes"}
    row = obj(value, path, required | {"sleepOnset", "latencyMinutes", "awakeningCount"}, required)
    wake_date = row["wakeDate"]
    if not isinstance(wake_date, str) or not DAY.fullmatch(wake_date):
        fail(path + ".wakeDate", "기상 날짜는 YYYY-MM-DD여야 합니다.")
    try:
        date.fromisoformat(wake_date)
    except ValueError:
        fail(path + ".wakeDate", "유효한 기상 날짜가 필요합니다.")
    for field in ("bedtime", "wakeTime", "sleepOnset"):
        if field not in row:
            continue
        if not isinstance(row[field], str) or not CLOCK.fullmatch(row[field]):
            fail(path + "." + field, "시각은 HH:MM이어야 합니다.")
    if "sleepOnset" in row and "latencyMinutes" in row:
        fail(path, "sleepOnset과 latencyMinutes 중 하나만 보내세요.")
    asleep = number(row["asleepMinutes"], path + ".asleepMinutes", 1, 960, True)
    record = {"date": wake_date, "bedtime": row["bedtime"], "wake": row["wakeTime"],
              "sleep_hours": asleep / 60}
    if "sleepOnset" in row:
        record["sleep_onset"] = row["sleepOnset"]
    if "latencyMinutes" in row:
        record["latency_minutes"] = number(row["latencyMinutes"], path + ".latencyMinutes", 0, 960, True)
    if "awakeningCount" in row:
        record["awakening_count"] = number(row["awakeningCount"], path + ".awakeningCount", 0, 1000, True)
    return record


def calculate(raw):
    fields = {"contractVersion", "asOfAt", "timezone", "wakeDeadlineAt",
              "minInBedMinutes", "maxInBedMinutes", "history"}
    request = obj(raw, "$", fields, fields)
    enum(request["contractVersion"], "contractVersion", [CONTRACT])
    enum(request["timezone"], "timezone", ["Asia/Seoul"])
    now = at(request["asOfAt"], "asOfAt").astimezone(KST)
    deadline = at(request["wakeDeadlineAt"], "wakeDeadlineAt")
    if not timedelta(0) < deadline - now <= timedelta(hours=36):
        fail("wakeDeadlineAt", "기상 마감은 기준 시각 이후 36시간 이내여야 합니다.",
             "UNSUPPORTED_HORIZON", 422)
    minimum = number(request["minInBedMinutes"], "minInBedMinutes", 1, 960, True)
    maximum = number(request["maxInBedMinutes"], "maxInBedMinutes", minimum, 960, True)
    rows = array(request["history"], "history", 90)
    if len(rows) < 3:
        fail("history", "서로 다른 기상 날짜의 수면 기록이 3개 이상 필요합니다.")
    history = [history_record(row, index) for index, row in enumerate(rows)]
    try:
        from .sleep_planner_v1 import NoFeasiblePlan, recommend
        from threadpoolctl import threadpool_limits
    except ImportError as error:
        raise APIError("MODEL_UNAVAILABLE", "AI-06 실행 의존성을 사용할 수 없습니다.", 503) from error
    try:
        with threadpool_limits(limits=1):
            result = recommend({"deadline": deadline.isoformat(), "min_hours": minimum / 60,
                                "max_hours": maximum / 60, "history": history}, now=now)
    except NoFeasiblePlan as error:
        raise APIError("NO_FEASIBLE_PLAN", str(error), 422) from None
    except ValueError as error:
        raise APIError("INVALID_INPUT", str(error)) from None
    return {"result": {"contractVersion": CONTRACT, **result}, "modelVersion": MODEL_VERSION}
