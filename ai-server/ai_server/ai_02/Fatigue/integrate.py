"""Score the shared demo input and return one experimental fatigue index."""

import argparse
import json
import math
import sys
from pathlib import Path

from .model import BEDTIME_BANDS, HERE, MODEL_IDS, InputError, load_models, predict_all


def to_index(probability, threshold):
    if not 0 <= probability <= 1:
        raise ValueError("모델 확률은 0~1이어야 합니다.")
    if probability <= threshold:
        return 50 * probability / threshold
    return 50 + 50 * (probability - threshold) / (1 - threshold)


def run_models(values, *, allow_missing_habits=False):
    if not isinstance(values, dict):
        raise InputError("입력값을 JSON 객체로 보내 주세요.")
    daily_fields = ("asleep", "in_bed", "bed_at", "mean_3d", "mean_7d", "days_7d")
    if values.get("days_7d") == 0 or any(values.get(key) is None for key in daily_fields):
        return {name: {"error": "최근 수면 기록이 부족합니다." if name == "M1_H0" else
                       "최근 수면 기록 확인 단계에서 실행을 중단했습니다."} for name in MODEL_IDS}
    return predict_all(values, allow_missing_habits=allow_missing_habits)


def model_indices(results, thresholds):
    return {name: to_index(results[name]["riskProbability"], thresholds[name])
            for name in MODEL_IDS if "riskProbability" in results[name]}


def combine(indices, weights):
    if (set(weights) != set(MODEL_IDS) or
            any(not math.isfinite(w) or w < 0 for w in weights.values()) or
            not math.isclose(sum(weights.values()), 1) or weights["M1_H0"] <= 0):
        raise ValueError("가중치는 모델 세 개에 대해 합이 1이어야 하며 M1 비중은 양수여야 합니다.")
    if "M1_H0" not in indices:
        return None, {}
    active = {name: weights[name] for name in MODEL_IDS if name in indices and weights[name] > 0}
    total = sum(active.values())
    effective = {name: weight / total for name, weight in active.items()}
    return sum(indices[name] * weight for name, weight in effective.items()), effective


def predict_fatigue(values, *, weights=None, allow_missing_habits=False):
    results = run_models(values, allow_missing_habits=allow_missing_habits)
    return summarize_fatigue(results, weights=weights)


def summarize_fatigue(results, *, weights=None):
    if weights is None:
        weights = json.loads((HERE / "weights.json").read_text(encoding="utf-8"))["weights"]
    thresholds = {name: model["threshold"] for name, model in load_models().items()}
    indices = model_indices(results, thresholds)
    score, applied = combine(indices, weights)
    return {
        "fatigueScore": round(score, 2) if score is not None else None,
        "status": ("insufficient_recent_sleep" if score is None else
                   "partial" if len(indices) < len(MODEL_IDS) else "ok"),
        "appliedWeights": applied,
        "unavailable": {name: row["error"] for name, row in results.items() if "error" in row},
        "researchOnly": True,
    }


def prompt_inputs():
    values = {"days_7d": int(input("최근 7일 관측 수 (0~8): "))}
    if values["days_7d"] == 0:
        return values
    fields = [
        ("asleep", "최근 실제 수면시간 (분)", int),
        ("in_bed", "최근 침대 체류시간 (분)", int),
        ("bed_at", "최근 취침 시각 (HH:MM)", str),
        ("mean_3d", "최근 3일 평균 실제 수면시간 (분)", float),
        ("mean_7d", "최근 7일 평균 실제 수면시간 (분)", float),
    ]
    if values["days_7d"] >= 3:
        fields.extend([
            ("std_7d", "7일 수면시간 표준편차 (분)", float),
            ("bedtime_var_7d", "7일 취침 시각 원형분산 (0~1)", float),
        ])
    for key, label, convert in fields:
        values[key] = convert(input(f"{label}: ").strip())
    values["habit_minutes"] = int(input("평소 평균 수면시간 (분): "))
    print("평소 취침 시간대  1 오후 10시 전 / 2 오후 10시~자정 / 3 자정~오전 2시 / "
          "4 오전 2~4시 / 5 오전 4시 이후")
    choice = int(input("시간대 번호 (1~5): "))
    if not 1 <= choice <= len(BEDTIME_BANDS):
        raise InputError("평소 취침 시간대는 1~5 중에서 선택해 주세요.")
    values["habit_bedtime"] = BEDTIME_BANDS[choice - 1]
    values["quality"] = int(input("평소 수면의 질 (0 매우 나쁨~4 매우 좋음): "))
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", nargs="?", help="공통 입력 JSON 경로. -는 표준 입력")
    args = parser.parse_args()
    try:
        if args.input is None:
            values = prompt_inputs()
        elif args.input == "-":
            values = json.load(sys.stdin)
        else:
            values = json.loads(Path(args.input).read_text(encoding="utf-8-sig"))
        result = predict_fatigue(values)
    except (ValueError, EOFError, OSError) as error:
        parser.exit(2, f"실행 오류: {error}\n")
    print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))


if __name__ == "__main__":
    main()
