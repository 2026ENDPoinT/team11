package com.team11.backend.plans.domain;

/** READY/BASELINE/TRADEOFF는 AI-03 상태 그대로, NO_FEASIBLE_PLAN은 bed/wake 없음(AI-03 selected=null, AI-06 422). */
public enum PlanStatus {
    READY, BASELINE, TRADEOFF, NO_FEASIBLE_PLAN
}
