"""Liveness response only; no inference, network calls, or DB access."""

from ..ai_02.service import MODEL_VERSION
from ..ai_03.scenarios import MODEL_VERSION as EXAM_VERSION
from ..ai_06.service import MODEL_VERSION as FOCUS_VERSION


def health():
    return {
        "status": "ok",
        "modelVersion": MODEL_VERSION,
        "models": {"AI-02": MODEL_VERSION, "AI-03": EXAM_VERSION, "AI-06": FOCUS_VERSION},
    }
