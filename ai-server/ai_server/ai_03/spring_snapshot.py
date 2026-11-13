"""Project the published SleepSync DTOs onto the exam planner contract."""

from datetime import timedelta

from .backend import BACKEND_CONTRACT, calculate_backend
from .intervals import wire
from .schemas import array, at, fail, obj

SPRING_CONTRACT = "SLEEPSYNC_EXAM_SNAPSHOT_V1"
SETTINGS = ("sleepNeedMin", "sleepLatencyMin", "minSleepMin", "commuteMin", "prepMin", "bufferMin")


def project(row, fields):
    return {field: row[field] for field in fields if field in row}


def rows(payload, field, limit):
    for index, row in enumerate(array(payload.get(field, []), field, limit)):
        if not isinstance(row, dict):
            fail(f"{field}[{index}]", "객체가 필요합니다.")
        yield row


def clip_stages(stages, lower):
    clipped = []
    for stage in rows({"stages": stages}, "stages", 10000):
        start = at(stage.get("startAt"), "sleepLogs.stages.startAt")
        end = at(stage.get("endAt"), "sleepLogs.stages.endAt")
        if end <= start:
            fail("sleepLogs.stages", "단계 종료는 시작 이후여야 합니다.")
        if end > lower:
            clipped.append(dict(stage, startAt=wire(max(lower, start))))
    return clipped


def adapt_snapshot(payload):
    required = {"contractVersion", "asOfAt", "sourceRevision", "selection",
                "userProfile", "events", "timetableEntries", "sleepLogs"}
    obj(payload, "$", required | {"semesters", "historyCoverage", "targetEventId",
        "targetOccurrenceKey", "populationScope", "calendarState", "includeNaps",
        "napWindows", "usualSleep", "randomSeed"}, required)
    if payload["contractVersion"] != SPRING_CONTRACT:
        fail("contractVersion", "지원하지 않는 Spring 스냅샷입니다.")
    now = at(payload["asOfAt"], "asOfAt")
    profile = payload["userProfile"]
    if not isinstance(profile, dict) or not isinstance(profile.get("settings"), dict):
        fail("userProfile.settings", "사용자 설정 객체가 필요합니다.")
    mapped_profile = project(profile["settings"], SETTINGS)
    mapped_profile.update(project(profile, ("prepMinEstimate",)))
    if "usualSleep" in payload:
        usual = obj(payload["usualSleep"], "usualSleep",
                    {"usualBedtime", "usualWakeTime"}, ["usualBedtime", "usualWakeTime"])
        mapped_profile.update(usual)

    semesters = {row["semester"]: row for row in rows(payload, "semesters", 100)
                 if isinstance(row.get("semester"), str)}
    timetable = []
    for row in rows(payload, "timetableEntries", 100):
        entry = project(row, ("entryId", "dayOfWeek", "startTime", "endTime"))
        semester = row.get("semester")
        if semester is not None:
            if not isinstance(semester, str) or semester not in semesters:
                fail("timetableEntries.semester", "수업의 학기 날짜를 함께 보내거나 대상 학기 수업만 보내고 semester를 생략하세요.")
            bounds = semesters[semester]
            for source, target in (("startDate", "semesterStartDate"), ("endDate", "semesterEndDate")):
                if bounds.get(source) is not None:
                    entry[target] = bounds[source]
        timetable.append(entry)

    lower = now - timedelta(days=28)
    sleep = []
    for row in rows(payload, "sleepLogs", 200):
        if row.get("status") == "OPEN" or row.get("wakeAt") is None:
            continue
        start, end = at(row.get("bedAt"), "sleepLogs.bedAt"), at(row["wakeAt"], "sleepLogs.wakeAt")
        if end <= lower:
            continue
        log = project(row, ("sleepLogId", "bedAt", "wakeAt", "source", "stages"))
        # The public DTO does not say whether durationMin is actual sleep.
        # Preserve observed stages and let the model infer sleep from them.
        log["durationBasis"] = "UNKNOWN"
        if start < lower:
            log["bedAt"] = wire(lower)
            if "stages" in log:
                log["stages"] = clip_stages(log["stages"], lower)
        sleep.append(log)
    coverage = payload.get("historyCoverage", {
        "startAt": wire(lower), "endAt": wire(now), "state": "PARTIAL" if sleep else "NONE",
    })
    request = project(payload, ("asOfAt", "sourceRevision", "selection", "targetEventId",
                      "targetOccurrenceKey", "populationScope", "calendarState",
                      "includeNaps", "napWindows", "randomSeed"))
    request.update(contractVersion=BACKEND_CONTRACT, profile=mapped_profile,
                   events=[project(row, ("eventId", "type", "startAt", "endAt", "allDay", "repeat"))
                           for row in rows(payload, "events", 500)],
                   timetableEntries=timetable, sleepLogs=sleep, historyCoverage=coverage)
    return request


def calculate_spring_snapshot(payload):
    return calculate_backend(adapt_snapshot(payload))
