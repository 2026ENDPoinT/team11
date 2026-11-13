package com.team11.backend.plans.dto;

import com.team11.backend.plans.domain.ExamNature;
import com.team11.backend.plans.domain.PreparationState;

/** 시험 모드 전용 입력(공부 모드 시트). eventId 생략 시 AI가 72시간 내 가장 가까운 시험을 고른다. 다른 모드는 바디 없이 호출. */
public record GeneratePlanRequest(Long eventId, PreparationState preparationState, ExamNature examNature) {

    public static final GeneratePlanRequest EMPTY = new GeneratePlanRequest(null, null, null);
}
