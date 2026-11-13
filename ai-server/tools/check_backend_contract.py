"""Run all deployed API snapshots through HTTP(S), without logging the key/body."""

import argparse
import json
import os
from pathlib import Path
import ssl
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4


def main():
    parser = argparse.ArgumentParser(description="Backend-to-AI contract smoke check")
    parser.add_argument("base_url", help="e.g. https://ai.internal.example/ai/v1")
    parser.add_argument("--ca-file", help="Trusted private CA; certificate verification stays enabled")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    from urllib.parse import urlsplit
    url = urlsplit(base)
    if url.query or url.fragment or url.username or not url.path.endswith("/ai/v1"):
        parser.error("base URL must end with /ai/v1 and have no credentials/query")
    if url.scheme != "https" and not (url.scheme == "http" and url.hostname in {"localhost", "127.0.0.1", "::1"}):
        parser.error("Use HTTPS except for loopback development")
    key = os.environ.get("AI_INTERNAL_KEY")
    if not key:
        parser.error("Set AI_INTERNAL_KEY")
    context = ssl.create_default_context(cafile=args.ca_file)
    root = Path(__file__).resolve().parents[1]
    failures = 0
    calls = [("AI-01", "/health", None), ("READY", "/ready", None),
             ("AI-02", "/plan/tonight", "ai02"), ("AI-03", "/plan/exam-sleep", "ai07"),
             ("AI-04", "/sleep-debt/calculate", "ai04"), ("AI-05", "/insights/risks", "ai05"),
             ("AI-06", "/plan/focus-sleep", "ai06-focus")]
    for name, path, example in calls:
        request_id = "smoke-" + uuid4().hex
        body = (root / "docs/examples" / (example + "-request.json")).read_bytes() if example else None
        target = base[:-len('/ai/v1')] + path if path.startswith('/ai/') else base + path
        request = Request(target, data=body, headers={"X-Internal-Key": key,
                          "X-Request-Id": request_id, "Content-Type": "application/json"})
        try:
            with urlopen(request, timeout=12 if name in {"AI-03", "AI-06"} else 8, context=context) as response:
                result = json.load(response)
                valid = response.status == 200 and response.headers.get("X-Request-Id") == request_id
                if example == "ai07":
                    valid = valid and result.get("modelVersion", "").startswith("exam-sleep-workload-") and result.get("result", {}).get("contractVersion") == "EXAM_SLEEP_MINIMAL_V1"
                elif example == "ai06-focus":
                    plan = result.get("result", {})
                    recommendation = plan.get("recommendation", {})
                    valid = (valid and result.get("modelVersion") == "focus-sleep-pvt-1.0.0"
                             and plan.get("contractVersion") == "FOCUS_SLEEP_V1"
                             and plan.get("recommendation_validated") is False
                             and 6 <= recommendation.get("sleep_opportunity_hours", 0) <= 8)
                elif example:
                    expected = json.loads((root / "docs/examples" / (example + "-response.json")).read_text(encoding="utf-8"))
                    valid = valid and result == expected
                else:
                    valid = valid and (result.get("ready") is True if name == "READY" else result.get("status") == "ok")
                if not valid:
                    raise ValueError("contract mismatch")
            print(name + " OK")
        except (HTTPError, URLError, OSError, ValueError):
            failures += 1
            print(name + " FAILED", file=sys.stderr)
    return int(failures > 0)


if __name__ == "__main__":
    raise SystemExit(main())
