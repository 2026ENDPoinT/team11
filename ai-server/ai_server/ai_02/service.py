"""AI-02 orchestration and public response assembly; models only calculate."""

import json
import math

from ..errors import APIError
from .preprocessing import prepare_request
from .Sleep_plan.planner import analyze_plan, schedule, plan_dates
from .Fatigue.model import load_models, HERE, MODEL_IDS
from .Fatigue.integrate import combine

MODEL_VERSION = "ai02-normal-v1"

MESSAGES = {
    "FIRST_EVENT_DEADLINE": "첫 일정과 준비·통학 시간을 고려했어요.",
    "FREE_WAKE_REFERENCE": "자유일 평균 기상 시각을 참고했어요.",
    "NEAR_MINIMUM_COST": "수면 압력과 수면 습관을 함께 고려했어요.",
    "RULE_TARGET": "가능한 시간 안에서 목표 수면시간을 우선했어요.",
    "AWAKENINGS_NOT_ESTIMATED": "관측되지 않은 수면 중 각성은 추정하지 않았어요.",
    "INVALID_OR_OVERLAPPING_LOGS": "오류가 있거나 겹치는 최근 기록은 제외했어요.",
    "UNOBSERVED_SLEEP_WAKE_GAP": "수면·각성 이력에 긴 미관측 구간이 있어요.",
    "INSUFFICIENT_HISTORY": "최근 관측일이 부족해 규칙 계획을 사용했어요.",
    "FATIGUE_UNAVAILABLE": "현재 피로 지표를 계산할 수 없어 해당 항을 제외했어요.",
    "FATIGUE_PARTIAL": "일부 피로 모델만 사용했어요.",
    "HABIT_WINDOW_RELAXED": "가능한 계획을 위해 평소 취침 시각 범위를 완화했어요.",
    "SENSITIVITY_FALLBACK": "계산의 민감도가 높아 규칙 계획을 사용했어요.",
    "FREE_WAKE_ADJUSTED": "수면 제약을 만족하도록 자유일 기상을 늦췄어요.",
    "BELOW_7_HOURS": "가능한 수면시간이 7시간보다 짧아요.",
    "SLEEP_GOAL_NOT_MET": "일정 제약으로 목표 수면시간을 확보하지 못했어요.",
}


def calculate(raw):
    payload = prepare_request(raw)
    try:
        models = load_models()
        for name in MODEL_IDS:
            threshold = models[name]["threshold"]
            if type(threshold) not in (int, float) or not math.isfinite(threshold) or not 0 < threshold < 1:
                raise ValueError("invalid model threshold")
        weights = json.loads((HERE / "weights.json").read_text(encoding="utf-8"))["weights"]
        combine({}, weights)
    except (OSError, ValueError, KeyError, TypeError) as error:
        raise APIError("MODEL_UNAVAILABLE", "피로 모델 산출물 또는 가중치를 사용할 수 없습니다.", 503) from None
    try:
        decision = analyze_plan(payload)
    except (ValueError, KeyError, TypeError, OverflowError) as error:
        raise APIError("INVALID_INPUT", f"계산 입력 오류: {error}") from None
    if decision["plan"] is None:
        raise APIError("SOLVER_INFEASIBLE", "현재 시각과 일정에서 최소 수면시간을 만족하는 계획이 없습니다.", 422)
    _, target = plan_dates(payload)
    _, activities, _ = schedule(payload, target)
    profile = payload["profile"]
    prep = profile.get("prepMinEstimate")
    if prep is None:
        prep = profile["prepMin"]
    breakdown = None if not activities else {
        "commuteMin": profile["commuteMin"], "prepMin": prep,
        "bufferMin": profile["bufferMin"], "sleepNeedMin": profile["sleepNeedMin"],
        "sleepLatencyMin": profile["sleepLatencyMin"],
    }
    return {"result": {
        **decision["plan"], "breakdown": breakdown,
        "reasons": [MESSAGES.get(code, code) for code in decision["reasons"]],
        "warnings": [MESSAGES.get(code, code) for code in decision["warnings"]],
    }, "modelVersion": MODEL_VERSION}
