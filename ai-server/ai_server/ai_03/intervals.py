"""UTC interval algebra; all intervals are half-open."""
from datetime import datetime, timedelta, timezone
import math

UTC = timezone.utc
KST = timezone(timedelta(hours=9), 'Asia/Seoul')


def minutes(a, b):
    return (b-a).total_seconds()/60


def merge(rows):
    out = []
    for a, b in sorted(rows):
        if b <= a:
            continue
        if out and a <= out[-1][1]:
            out[-1] = (out[-1][0], max(b, out[-1][1]))
        else:
            out.append((a, b))
    return out


def free(start, end, blocked):
    out, cursor = [], start
    for a, b in merge(blocked):
        a, b = max(start, a), min(end, b)
        if b <= a:
            continue
        if a > cursor:
            out.append((cursor, a))
        cursor = max(cursor, b)
    if cursor < end:
        out.append((cursor, end))
    return out


def overlap(a, b, c, d):
    return max(0., minutes(max(a, c), min(b, d)))


def contains(rows, a, b):
    return any(c <= a and b <= d for c, d in rows)


def grid(value, direction=1, step=15):
    seconds = step*60
    n = value.timestamp()/seconds
    return datetime.fromtimestamp((math.ceil(n) if direction > 0 else math.floor(n))*seconds, UTC)


def day_parts(a, b):
    while a < b:
        local = a.astimezone(KST)
        boundary = (local+timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0).astimezone(UTC)
        right = min(b, boundary)
        yield local.date().isoformat(), a, right
        a = right


def wire(value):
    return value.astimezone(KST).isoformat()


def canonical_hash(value):
    import hashlib, json
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False,
                                     allow_nan=False).encode()).hexdigest()
