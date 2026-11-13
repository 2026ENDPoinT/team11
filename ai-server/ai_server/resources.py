"""Pinned deployable model resources; no API can regenerate the manifest."""

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "resource_manifest.json"


def build_manifest():
    paths = []
    for package in ("ai_02/Sleep_plan", "ai_02/Fatigue", "ai_03", "ai_04", "ai_05", "ai_06"):
        paths.extend((ROOT / package).glob("*.py"))
    paths.extend(ROOT / p for p in ("ai_02/Fatigue/models.json",
                 "ai_02/Fatigue/weights.json", "smoke/ai02.json", "smoke/ai03.json",
                 "smoke/ai04.json", "smoke/ai05.json", "smoke/ai06.json"))
    paths.extend((ROOT / "ai_03/config").glob("*.json"))
    paths.extend((ROOT / "ai_03/artifacts").glob("*.json"))
    paths.extend(ROOT / "ai_06/sleep-only-cognition-v2" / filename
                 for filename in ("selection.json", "summary.json", "tree3.joblib"))
    return {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(set(paths))}


def inspect_resources():
    try:
        expected = json.loads(MANIFEST.read_text(encoding="utf-8"))
        if not isinstance(expected, dict) or not expected:
            raise ValueError
        actual = build_manifest()
        changed = sorted(key for key in set(expected) | set(actual) if expected.get(key) != actual.get(key))
        return {"ok": not changed, "changedResources": changed}
    except (OSError, ValueError, TypeError):
        return {"ok": False, "changedResources": ["unreadable_resource_or_manifest"]}


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-manifest", action="store_true")
    args = parser.parse_args()
    if args.write_manifest:
        MANIFEST.write_text(json.dumps(build_manifest(), indent=2) + "\n", encoding="utf-8")
    else:
        result = inspect_resources()
        print(json.dumps(result))
        raise SystemExit(0 if result["ok"] else 1)
