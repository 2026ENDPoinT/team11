"""Evaluate the exported models and validate the shared inputs."""

import json
import math
import re
from functools import lru_cache
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODEL_IDS = ("M1_H0", "M2_CORE", "M3_CORE")

BEDTIME_BANDS = (
    "before 10 pm", "10 pm–12 am", "12 am–2 am", "2 am–4 am", "after 4 am",
)


class InputError(ValueError):
    pass


def number(values, key, label, low, high, *, whole=False):
    value = values.get(key)
    valid_type = type(value) is int if whole else type(value) in (int, float)
    if not valid_type or not low <= value <= high:
        kind = "정수" if whole else "숫자"
        raise InputError(f"{label}에 {low}~{high} 사이의 {kind}를 입력해 주세요.")
    return value


def habit_duration_bands(minutes):
    if minutes >= 420:
        return ("7–8 hours", "7-8 hours") if minutes <= 480 else ("more than 8 hours",) * 2
    if minutes >= 360:
        return "6–7 hours", "6-7 hours"
    if minutes >= 300:
        return "5–6 hours", "4-5 hours" if minutes == 300 else None
    return "less than 5 hours", "4-5 hours" if minutes >= 240 else "less than 4 hours"


@lru_cache(maxsize=1)
def load_models():
    bundle = json.loads((HERE / "models.json").read_text(encoding="utf-8"))
    if bundle["version"] != 1 or set(bundle["models"]) != set(MODEL_IDS):
        raise ValueError("지원하지 않는 모델 파일 형식입니다.")
    return bundle["models"]


def sigmoid(value):
    if value >= 0:
        return 1 / (1 + math.exp(-value))
    exp = math.exp(value)
    return exp / (1 + exp)


def daily_probability(model, inputs):
    values = [inputs[name] for name in model["features"]]
    numeric = [median if value is None else value
               for value, median in zip(values, model["medians"], strict=True)]
    numeric.extend(int(values[index] is None) for index in model["missingIndicators"])
    logits = math.fsum(
        (value - mean) / scale * coefficient
        for value, mean, scale, coefficient in zip(
            numeric, model["means"], model["scales"], model["coefficients"], strict=True
        )
    ) + model["intercept"]
    raw = min(max(sigmoid(logits), 1e-6), 1 - 1e-6)
    log_odds = math.log(raw / (1 - raw))
    return sigmoid(model["calibrationCoefficient"] * log_odds + model["calibrationIntercept"])


def score(model_id, inputs):
    model = load_models()[model_id]
    if model_id == "M1_H0":
        probability = daily_probability(model, inputs)
    else:
        probabilities = model["probabilities"][inputs["duration"]]
        if model_id == "M2_CORE":
            probabilities = probabilities[inputs["bedtime"]]
        probability = probabilities[inputs["quality"]]
    return {
        "modelId": model_id,
        "researchOnly": True,
        "riskClass": model["labels"][int(probability >= model["threshold"])],
        "riskProbability": probability,
        "inputs": inputs,
    }


def predict_all(values, *, allow_missing_habits=False):
    if not isinstance(values, dict):
        raise InputError("입력값을 JSON 객체로 보내 주세요.")
    asleep = number(values, "asleep", "최근 실제 수면시간", 1, 1440, whole=True)
    in_bed = number(values, "in_bed", "최근 침대 체류시간", asleep, 1440, whole=True)
    bed_at = values.get("bed_at")
    if not isinstance(bed_at, str) or not re.fullmatch(r"(?:[01][0-9]|2[0-3]):[0-5][0-9]", bed_at):
        raise InputError("최근 취침 시각을 24시간제 HH:MM으로 입력해 주세요.")
    hours, minutes = map(int, bed_at.split(":"))
    angle = math.tau * (60 * hours + minutes) / 1440
    days = number(values, "days_7d", "최근 7일 관측 수", 1, 8, whole=True)
    daily = {
        "asleep": asleep,
        "in_bed": in_bed,
        "bed_sin": math.sin(angle),
        "bed_cos": math.cos(angle),
        "mean_3d": number(values, "mean_3d", "최근 3일 평균 실제 수면시간", 1, 1440),
        "mean_7d": number(values, "mean_7d", "최근 7일 평균 실제 수면시간", 1, 1440),
        "std_7d": None if days < 3 else number(
            values, "std_7d", "최근 7일 수면시간 표준편차", 0, 720
        ),
        "bedtime_var_7d": None if days < 3 else number(
            values, "bedtime_var_7d", "최근 7일 취침 시각 원형분산", 0, 1
        ),
        "days_7d": days,
    }
    typical = values.get("habit_minutes")
    if typical is not None or not allow_missing_habits:
        typical = number(values, "habit_minutes", "평소 평균 수면시간", 1, 1440, whole=True)
    bedtime = values.get("habit_bedtime")
    if (bedtime is not None or not allow_missing_habits) and bedtime not in BEDTIME_BANDS:
        raise InputError("평소 취침 시간대를 선택해 주세요.")
    quality = values.get("quality")
    if quality is not None or not allow_missing_habits:
        quality = number(values, "quality", "평소 수면의 질", 0, 4, whole=True)
    band_m2, band_m3 = habit_duration_bands(typical) if typical is not None else (None, None)
    results = {
        "M1_H0": score("M1_H0", daily),
    }
    missing = [label for value, label in ((typical, "평소 평균 수면시간"), (quality, "평소 수면의 질"))
               if value is None]
    m2_missing = missing + (["평소 취침 시간대"] if bedtime is None else [])
    if m2_missing:
        results["M2_CORE"] = {"error": f"입력 부족 ({', '.join(m2_missing)})"}
    else:
        results["M2_CORE"] = score("M2_CORE", {"duration": band_m2, "bedtime": bedtime, "quality": quality})
    if missing:
        results["M3_CORE"] = {"error": f"입력 부족 ({', '.join(missing)})"}
    elif band_m3 is None:
        results["M3_CORE"] = {
            "error": "M3의 원본 설문에는 5시간 초과~6시간 미만에 해당하는 수면시간 구간이 없습니다."
        }
    else:
        results["M3_CORE"] = score("M3_CORE", {"duration": band_m3, "quality": quality})
    return results


