"""Small, replaceable internal contract. Durations are integer minutes."""

from dataclasses import dataclass, fields
from datetime import date, datetime, time
from zoneinfo import ZoneInfo


def integer(value, name, minimum=0, maximum=1440):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError(f"{name}: integer in [{minimum}, {maximum}] required")
    return value


def local_datetime(value, timezone="Asia/Seoul"):
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("timestamp must include timezone offset")
    if parsed.second or parsed.microsecond:
        raise ValueError("timestamps must have minute precision")
    return parsed.astimezone(ZoneInfo(timezone))


def clock(value):
    parsed = time.fromisoformat(value)
    if parsed.tzinfo or parsed.second or parsed.microsecond:
        raise ValueError("clock must be local HH:MM")
    return parsed


@dataclass(frozen=True)
class Config:
    timezone: str = "Asia/Seoul"
    window_days: int = 7
    plan_days: int = 3
    default_target_minutes: int = 480
    grid_minutes: int = 15
    earliest_bedtime: str = "20:00"
    latest_wake_time: str = "11:00"
    recovery_policy: str = "model"
    adaptive_bands: tuple = ((120, 15), (360, 30), (None, 60))
    max_extra_opportunity_minutes: int = 60
    extra_opportunity_minutes: int = 0
    model_warmup_days: int = 28
    rule_version: str = "opportunity-v4-history-calendar"

    @classmethod
    def from_dict(cls, values=None):
        values = dict(values or {})
        unknown = set(values) - {f.name for f in fields(cls)}
        if unknown:
            raise ValueError(f"unknown config fields: {sorted(unknown)}")
        # Preserve old explicit fixed-extra calls; current config uses adaptive mode.
        if "extra_opportunity_minutes" in values and "recovery_policy" not in values:
            values["recovery_policy"] = "fixed"
        if "adaptive_bands" in values:
            values["adaptive_bands"] = tuple(tuple(band) for band in values["adaptive_bands"])
        result = cls(**values)
        # This first version intentionally supports Korea wall-clock planning only.
        if result.timezone != "Asia/Seoul":
            raise ValueError("v0.1 supports Asia/Seoul only")
        integer(result.window_days, "window_days", 1, 60)
        integer(result.plan_days, "plan_days", 1, 7)
        integer(result.default_target_minutes, "default_target_minutes", 420, 720)
        integer(result.grid_minutes, "grid_minutes", 5, 60)
        integer(result.extra_opportunity_minutes, "extra_opportunity_minutes", 0, 60)
        integer(result.max_extra_opportunity_minutes, "max_extra_opportunity_minutes", 0, 60)
        if result.recovery_policy not in {"adaptive", "fixed", "model"}:
            raise ValueError("recovery_policy must be adaptive, fixed or model")
        integer(result.model_warmup_days, "model_warmup_days", 7, 90)
        if not result.adaptive_bands or len(result.adaptive_bands) > 12:
            raise ValueError("adaptive_bands must contain 1 to 12 bands")
        previous_limit, previous_extra = 0, 0
        for index, band in enumerate(result.adaptive_bands):
            if len(band) != 2:
                raise ValueError("each adaptive band must be [inclusive_upper_minutes, extra_minutes]")
            limit, extra = band
            integer(extra, "band extra minutes", 0, 60)
            if extra < previous_extra:
                raise ValueError("band extras must be nondecreasing")
            if index == len(result.adaptive_bands) - 1:
                if limit is not None:
                    raise ValueError("last band upper limit must be null")
            else:
                integer(limit, "band upper limit", previous_limit + 1, 86400)
                previous_limit = limit
            previous_extra = extra
        if clock(result.earliest_bedtime).hour < 12:
            raise ValueError("earliest_bedtime must be afternoon/evening")
        if clock(result.latest_wake_time).hour >= 12:
            raise ValueError("latest_wake_time must be before noon")
        return result


def parse_date(value):
    return date.fromisoformat(value)
