"""Rename external fields at the boundary; core code never imports backend DTOs."""

from ..ai_03.schemas import array, fail, obj
from ..serialization import camel
from .service import analyze


def remap_fields(source, canonical_to_external):
    """Missing values stay absent, so the core can distinguish missing from zero."""
    return {canonical: source[external] for canonical, external in canonical_to_external.items()
            if external in source}


def calculate_backend(payload):
    fields = {"now", "asOfDate", "preferences", "sleepRecords", "events",
              "schemaVersion", "windowDays", "planDays"}
    raw = obj(payload, "$", fields, {"now", "asOfDate", "preferences", "sleepRecords", "events"})
    preferences = remap_fields(obj(raw["preferences"], "preferences", {
        "adultConfirmed", "targetMinutes", "preparationCommuteMinutes", "sleepLatencyMinutes",
        "preferredBedtime", "preferredWakeTime",
    }), {
        "adult_confirmed": "adultConfirmed", "target_minutes": "targetMinutes",
        "preparation_commute_minutes": "preparationCommuteMinutes",
        "sleep_latency_minutes": "sleepLatencyMinutes", "preferred_bedtime": "preferredBedtime",
        "preferred_wake_time": "preferredWakeTime",
    })
    records = []
    record_fields = {"date": "date", "status": "status", "target_minutes": "targetMinutes",
                     "actual_sleep_minutes": "actualSleepMinutes", "sleep_start": "sleepStart",
                     "sleep_end": "sleepEnd", "awake_minutes": "awakeMinutes", "nap_minutes": "napMinutes"}
    for i, item in enumerate(array(raw["sleepRecords"], "sleepRecords", 90)):
        row = obj(item, f"sleepRecords[{i}]", set(record_fields.values()), {"date"})
        records.append(remap_fields(row, record_fields))
    events, ids = [], set()
    event_fields = {"id": "eventId", "start": "startAt", "end": "endAt",
                    "title": "title", "description": "description", "lead_minutes": "leadMinutes"}
    label_fields = {"type": "type", "focus_demand": "focusDemand", "movability": "movability"}
    for i, item in enumerate(array(raw["events"], "events", 100)):
        path = f"events[{i}]"
        row = obj(item, path, set(event_fields.values()) | {"labels", "userOverride"},
                  {"eventId", "startAt", "endAt"})
        event_id = row["eventId"]
        if type(event_id) not in (int, str) or event_id == "" or event_id in ids:
            fail(path + ".eventId", "중복 없는 일정 ID가 필요합니다.")
        if events and type(event_id) is not type(events[0]["id"]):
            fail(path + ".eventId", "요청 안에서 일정 ID 자료형을 통일하세요.")
        ids.add(event_id)
        event = remap_fields(row, event_fields)
        for external, internal in (("labels", "labels"), ("userOverride", "user_override")):
            if external in row:
                values = obj(row[external], path + "." + external, set(label_fields.values()))
                event[internal] = remap_fields(values, label_fields)
        events.append(event)
    native = {"now": raw["now"], "as_of_date": raw["asOfDate"],
              "schema_version": raw.get("schemaVersion", "0.1"),
              "preferences": preferences, "sleep_records": records, "events": events}
    config = remap_fields(raw, {"window_days": "windowDays", "plan_days": "planDays"})
    try:
        result = analyze(native, config=config)
    except (ValueError, KeyError, TypeError, OverflowError) as exc:
        fail("$", str(exc))
    return {"result": camel(result), "modelVersion": "sleep-coach-v4-history-calendar"}
