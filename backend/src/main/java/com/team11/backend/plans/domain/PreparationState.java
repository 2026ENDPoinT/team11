package com.team11.backend.plans.domain;

/** AI-03 selection.preparationState. 추천을 요청하는 시점에 사용자가 고른다. */
public enum PreparationState {
    STARTING, STARTING_HEAVY, SEEN_NOT_RECALLABLE, PATCHING_GAPS, FINAL_REVIEW
}
