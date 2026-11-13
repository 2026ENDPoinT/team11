"""Normalize sleep logs and build a seven-day Process S timeline."""

import math
import statistics
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

from ..Fatigue.inputs import KST, build_model_input
from ..Fatigue.integrate import predict_fatigue

RECENT = timedelta(days=7)
LOOKBACK = timedelta(days=35)


def stamp(value):
    if not isinstance(value, str):
        raise ValueError("시각은 오프셋이 포함된 ISO 8601 문자열이어야 합니다.")
    parsed = datetime.fromisoformat(value)
    if parsed.utcoffset() is None:
        raise ValueError("시각에 UTC 오프셋이 필요합니다.")
    return parsed.astimezone(UTC)


def minutes(delta):
    return delta.total_seconds() / 60


def clock_minute(value):
    local = value.astimezone(KST)
    return local.hour * 60 + local.minute + local.second / 60


def circular_mean(values):
    angles = [math.tau * value / 1440 for value in values]
    if not angles:
        return None
    x = statistics.mean(map(math.cos, angles))
    y = statistics.mean(map(math.sin, angles))
    if math.hypot(x, y) < 1e-9:
        return None
    return (math.atan2(y, x) * 1440 / math.tau) % 1440


@dataclass(frozen=True)
class Segment:
    start: datetime
    end: datetime
    state: str


@dataclass(frozen=True)
class Night:
    bed: datetime
    wake: datetime
    segments: tuple[Segment, ...]
    quality: int | None

    @property
    def day(self):
        return self.wake.astimezone(KST).date()

    @property
    def sleep_min(self):
        return sum(minutes(part.end - part.start) for part in self.segments if part.state == "sleep")

    @property
    def midpoint(self):
        parts = [part for part in self.segments if part.state == "sleep"]
        offset = sum(minutes(part.end - part.start) * minutes(
            part.start + (part.end - part.start) / 2 - self.bed
        ) for part in parts) / self.sleep_min
        return self.bed + timedelta(minutes=offset)


def measured_segments(stages, bed, wake):
    if not isinstance(stages, list):
        raise ValueError("stages는 배열이어야 합니다.")
    ordered = sorted(
        (stamp(row["startAt"]), stamp(row["endAt"]), row["stage"]) for row in stages
    )
    current = bed
    result = []
    for start, end, kind in ordered:
        if not current <= start < end <= wake or kind not in {"AWAKE", "LIGHT", "DEEP", "REM"}:
            raise ValueError("웨어러블 수면 단계가 겹치거나 기록 범위를 벗어났습니다.")
        if current < start:
            result.append(Segment(current, start, "unknown"))
        result.append(Segment(start, end, "awake" if kind == "AWAKE" else "sleep"))
        current = end
    if current < wake:
        result.append(Segment(current, wake, "unknown"))
    return tuple(result)


def parse_night(row, bed, wake, latency):
    elapsed = minutes(wake - bed)
    if not 0 < elapsed <= 1440:
        raise ValueError("수면 기록 구간은 0~24시간이어야 합니다.")
    source = row.get("source", "MANUAL")
    if source not in {"WEARABLE", "TAP", "MANUAL"}:
        raise ValueError("지원하지 않는 수면 기록 출처입니다.")
    quality = row.get("quality")
    if quality is not None and (type(quality) is not int or not 1 <= quality <= 5):
        raise ValueError("수면의 질은 1~5 정수여야 합니다.")
    if row.get("date") is not None and row["date"] != wake.astimezone(KST).date().isoformat():
        raise ValueError("수면 기록의 date는 한국 시간 기상일이어야 합니다.")
    if source == "WEARABLE" and row.get("stages"):
        segments = measured_segments(row["stages"], bed, wake)
    else:
        duration = row.get("durationMin")
        if duration is not None and (type(duration) is not int or not 1 <= duration <= round(elapsed)):
            raise ValueError("수면시간이 기록 구간을 벗어났습니다.")
        # A minute-rounded bed-to-wake value still includes latency for manual logs.
        already_net = duration is not None and (source == "WEARABLE" or elapsed - duration > 1)
        if source == "WEARABLE" and duration is None:
            raise ValueError("웨어러블 실제 수면 구간이나 수면시간이 필요합니다.")
        onset = wake - timedelta(minutes=min(duration, elapsed)) if already_net else (
            bed + timedelta(minutes=latency)
        )
        if onset >= wake:
            raise ValueError("관측된 수면시간이 없습니다.")
        segments = ((Segment(bed, onset, "awake"),) if bed < onset else ()) + (
            Segment(onset, wake, "sleep"),
        )
    night = Night(bed, wake, segments, quality)
    if round(night.sleep_min) < 1:
        raise ValueError("관측된 수면시간이 없습니다.")
    return night


def clean_history(rows, now, latency):
    unique = {}
    ids = set()
    invalid_recent = 0
    for row in rows:
        wake = None
        try:
            if row.get("status") == "OPEN":
                continue
            wake = stamp(row["wakeAt"])
            if not now - LOOKBACK <= wake < now:
                continue
            if any(stamp(row[key]) >= now for key in ("createdAt", "updatedAt")
                   if row.get(key) is not None):
                continue
            if row.get("status") != "CLOSED":
                raise ValueError("수면 기록 상태 오류")
            bed = stamp(row["bedAt"])
            key = (bed, wake)
            log_id = row.get("sleepLogId")
            if key in unique or (log_id is not None and log_id in ids):
                continue
            unique[key] = parse_night(row, bed, wake, latency)
            if log_id is not None:
                ids.add(log_id)
        except (AttributeError, KeyError, TypeError, ValueError, OverflowError):
            invalid_recent += int(wake is None or wake >= now - RECENT)

    # Discard an entire overlapping group so its duration is not counted twice.
    ordered = sorted(unique.values(), key=lambda night: (night.bed, night.wake))
    kept = []
    group = []
    group_end = None
    for night in ordered:
        if group_end is not None and night.bed >= group_end:
            if len(group) == 1:
                kept.extend(group)
            else:
                invalid_recent += sum(item.wake >= now - RECENT for item in group)
            group = []
            group_end = None
        group.append(night)
        group_end = max(group_end, night.wake) if group_end is not None else night.wake
    if len(group) == 1:
        kept.extend(group)
    elif group:
        invalid_recent += sum(item.wake >= now - RECENT for item in group)
    return kept, invalid_recent


def recent_timeline(nights, now):
    start = now - RECENT
    cursor = start
    previous = None
    timeline = []
    for night in nights:
        bed = max(night.bed, start)
        if cursor < bed:
            known = previous is not None and (night.day - previous.day).days <= 1 and (
                bed - cursor <= timedelta(hours=26)
            )
            timeline.append(Segment(cursor, bed, "awake" if known else "unknown"))
        for part in night.segments:
            if part.end > start:
                timeline.append(Segment(max(part.start, start), part.end, part.state))
        previous = night
        cursor = night.wake
    if cursor < now:
        known = previous is not None and now - cursor <= timedelta(hours=24)
        timeline.append(Segment(cursor, now, "awake" if known else "unknown"))
    return timeline


def fatigue_from_history(nights, now):
    records = [
        {
            "status": "CLOSED",
            "bedAt": night.bed.isoformat(),
            "wakeAt": night.wake.isoformat(),
            "durationMin": round(night.sleep_min),
            "quality": night.quality,
        }
        for night in nights
    ]
    inputs, coverage = build_model_input(records, now, KST)
    return predict_fatigue(inputs, allow_missing_habits=True), {
        **coverage, "sleepDurationSource": "estimated_net_sleep_minutes",
    }
