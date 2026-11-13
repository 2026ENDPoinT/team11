"""Accounting metric relative to user targets, not a physiological debt balance."""

from datetime import timedelta

from .schemas import integer, local_datetime, parse_date


def calculate_shortfall(records, as_of_date, config):
    end = parse_date(as_of_date)
    start = end - timedelta(days=config.window_days - 1)
    seen, rows, intervals, ignored = set(), [], [], 0
    for record in records:
        day = parse_date(record["date"])
        if day in seen:
            raise ValueError(f"duplicate sleep date: {day}")
        seen.add(day)
        if day > end:
            raise ValueError("sleep record is after as_of_date")
        if day < start:
            ignored += 1
            continue
        status = record.get("status", "recorded")
        if status not in {"recorded", "missing", "all_nighter"}:
            raise ValueError("invalid sleep record status")
        target = integer(record.get("target_minutes", config.default_target_minutes),
                         "target_minutes", 420, 720)
        if status == "missing":
            if any(record.get(k) is not None for k in
                   ("actual_sleep_minutes", "sleep_start", "sleep_end", "awake_minutes", "nap_minutes")):
                raise ValueError("missing record cannot include sleep measurements")
            continue
        has_start = record.get("sleep_start") is not None
        has_end = record.get("sleep_end") is not None
        if has_start != has_end:
            raise ValueError("sleep_start and sleep_end must be supplied together")
        actual = record.get("actual_sleep_minutes")
        if status == "all_nighter":
            if has_start or (actual is not None and actual != 0) or record.get("awake_minutes", 0):
                raise ValueError("all_nighter must have zero night sleep and no interval")
            actual = 0
        elif has_start:
            begin = local_datetime(record["sleep_start"], config.timezone)
            finish = local_datetime(record["sleep_end"], config.timezone)
            if finish.date() != day:
                raise ValueError("record date must equal local final wake date")
            duration = int((finish - begin).total_seconds() / 60)
            integer(duration, "sleep interval minutes", 1, 1440)
            awake = integer(record.get("awake_minutes", 0), "awake_minutes", 0, duration)
            measured = duration - awake
            if actual is not None and actual != measured:
                raise ValueError("duration and timestamp measurements disagree")
            actual = measured
            intervals.append((begin, finish))
        elif record.get("awake_minutes", 0):
            raise ValueError("awake_minutes requires a sleep interval")
        if actual is None:
            raise ValueError("recorded sleep requires duration or timestamps")
        actual = integer(actual, "actual_sleep_minutes")
        nap = integer(record.get("nap_minutes", 0), "nap_minutes")
        if actual + nap > 1440:
            raise ValueError("night sleep plus naps cannot exceed 24h")
        rows.append({"date": str(day), "target_minutes": target,
                     "target_source": "record" if "target_minutes" in record else "config_default",
                     "actual_night_sleep_minutes": actual, "nap_minutes": nap,
                     "shortfall_minutes": max(0, target - actual),
                     "above_target_minutes": max(0, actual - target)})
    intervals.sort()
    if any(right[0] < left[1] for left, right in zip(intervals, intervals[1:])):
        raise ValueError("sleep intervals overlap")
    recorded = {row["date"] for row in rows}
    missing = [str(start + timedelta(days=i)) for i in range(config.window_days)
               if str(start + timedelta(days=i)) not in recorded]
    return {
        "metric": "target_night_sleep_shortfall", "window_start": str(start),
        "window_end": str(end), "window_days": config.window_days,
        "recorded_days": len(rows), "missing_dates": missing,
        "coverage_fraction": round(len(rows) / config.window_days, 4),
        "data_status": "no_records" if not rows else "partial" if missing else "complete",
        "shortfall_minutes": sum(r["shortfall_minutes"] for r in rows),
        "above_target_minutes": sum(r["above_target_minutes"] for r in rows),
        "nap_minutes": sum(r["nap_minutes"] for r in rows),
        "ignored_older_records": ignored, "daily": sorted(rows, key=lambda r: r["date"]),
        "physiological_debt_minutes": None, "estimated_recovery_completion": None,
        "rule_ids": ["PRODUCT_SHORTFALL_ACCOUNTING", "E_HISTORY", "E_NO_LINEAR_REPAYMENT"],
    }
