from .errors import APIError


def validate_limits(raw, limits):
    for field, maximum in limits.items():
        value = raw.get(field)
        if isinstance(value, list) and len(value) > maximum:
            raise APIError("INVALID_INPUT", f"{field}는 최대 {maximum}개입니다.", field=field)
    for field in ("sleepHistory", "recentSleep"):
        rows = raw.get(field)
        if isinstance(rows, list):
            for row in rows:
                stages = row.get("stages") if isinstance(row, dict) else None
                if isinstance(stages, list) and len(stages) > 500:
                    raise APIError("INVALID_INPUT", "수면 세션별 stages는 최대500개입니다.", field=field)
