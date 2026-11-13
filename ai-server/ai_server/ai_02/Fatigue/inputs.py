"""Pure conversion of supplied sleep records; no network access."""

import math
import statistics
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta, timezone

KST = timezone(timedelta(hours=9), "Asia/Seoul")
RECENT_WINDOW = timedelta(days=7)
HABIT_WINDOW = timedelta(days=28)
HABIT_MIN_NIGHTS = 7
QUALITY_MIN_NIGHTS = 3
PERSONALIZATION_LOOKBACK_DAYS = 92

class BackendError(RuntimeError):
    """The backend response could not be used to calculate model inputs."""

    def __init__(self, message, *, status=None):
        super().__init__(message)
        self.status = status


@dataclass(frozen=True)
class Night:
    bed: datetime
    wake: datetime
    in_bed: int
    duration: int
    quality: int | None


def timestamp(value):
    stamp = datetime.fromisoformat(value)
    if stamp.utcoffset() is None:
        raise ValueError("시간대 오프셋이 필요합니다.")
    return stamp.astimezone(UTC)


def main_nights(logs, start, cutoff, zone, *, strict=True):
    by_day = {}
    for log in logs:
        try:
            if log["status"] == "OPEN":
                continue
            if log["status"] != "CLOSED":
                raise ValueError
            wake = timestamp(log["wakeAt"])
            if not start <= wake < cutoff:
                continue
            if any(timestamp(log[key]) >= cutoff for key in ("createdAt", "updatedAt")
                   if log.get(key) is not None):
                continue
            bed = timestamp(log["bedAt"]).astimezone(zone)
            elapsed = (wake - bed.astimezone(UTC)).total_seconds() / 60
            in_bed = round(elapsed)
            duration = log["durationMin"]
            quality = log.get("quality")
            if (not 0 < elapsed <= 1440 or not 1 <= in_bed <= 1440
                    or type(duration) is not int or not 1 <= duration <= in_bed
                    or (quality is not None and (type(quality) is not int or not 1 <= quality <= 5))):
                raise ValueError
        except (KeyError, TypeError, ValueError, OverflowError):
            if not strict:
                continue
            raise BackendError("완료된 수면 기록의 시각, durationMin 또는 quality 값이 올바르지 않습니다.") from None
        day = wake.astimezone(zone).date()
        night = Night(bed, wake, in_bed, duration, quality)
        previous = by_day.get(day)
        if previous is None or (duration, in_bed, wake) > (previous.duration, previous.in_bed, previous.wake):
            by_day[day] = night
    return sorted(by_day.values(), key=lambda night: night.wake)


def bedtime_vector(nights):
    angles = [
        math.tau * (bed.hour * 3600 + bed.minute * 60 + bed.second) / 86400
        for bed in (night.bed for night in nights)
    ]
    return statistics.mean(map(math.cos, angles)), statistics.mean(map(math.sin, angles))


def bedtime_band(nights):
    x, y = bedtime_vector(nights)
    if math.hypot(x, y) < 1e-9:
        return None
    minute = round(math.atan2(y, x) * 1440 / math.tau) % 1440
    if minute < 120:
        return "12 am–2 am"
    if minute <= 240:
        return "2 am–4 am"
    if minute < 720:
        return "after 4 am"
    if minute < 1320:
        return "before 10 pm"
    return "10 pm–12 am"


def build_model_input(logs, cutoff, zone):
    nights = main_nights(logs, cutoff - RECENT_WINDOW - HABIT_WINDOW, cutoff, zone)
    return _model_input_from_nights(nights, cutoff)


def _model_input_from_nights(nights, cutoff):
    habit_end = cutoff - RECENT_WINDOW
    recent = [night for night in nights if night.wake >= habit_end]
    last_three = [night for night in recent if night.wake >= cutoff - timedelta(days=3)]
    habit = [night for night in nights if night.wake < habit_end]
    ratings = [night.quality for night in habit if night.quality is not None]
    values = {"days_7d": len(recent)}
    if recent and last_three:
        latest = recent[-1]
        values.update(
            asleep=latest.duration,
            in_bed=latest.in_bed,
            bed_at=latest.bed.strftime("%H:%M"),
            mean_3d=statistics.mean(night.duration for night in last_three),
            mean_7d=statistics.mean(night.duration for night in recent),
        )
        if len(recent) >= 3:
            values["std_7d"] = statistics.pstdev(night.duration for night in recent)
            values["bedtime_var_7d"] = max(0, 1 - math.hypot(*bedtime_vector(recent)))
    if len(habit) >= HABIT_MIN_NIGHTS:
        values["habit_minutes"] = round(statistics.mean(night.duration for night in habit))
        values["habit_bedtime"] = bedtime_band(habit)
        if len(ratings) >= QUALITY_MIN_NIGHTS:
            values["quality"] = round(statistics.mean(ratings)) - 1
    return values, {
        "recent3dNights": len(last_three),
        "recent7dNights": len(recent),
        "habitNights": len(habit),
        "habitQualityNights": len(ratings),
        "sleepDurationSource": "durationMin_proxy",
        "habitSource": "sleep_logs_28d_before_recent_7d",
    }


