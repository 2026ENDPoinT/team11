package com.team11.backend.plans.dto;

import com.team11.backend.plans.domain.ExamNature;
import com.team11.backend.plans.domain.PreparationState;
import jakarta.validation.constraints.NotNull;

/** EXAM-08. eventId를 생략하면 AI가 72시간 내 가장 가까운 시험을 자동으로 고른다. */
public record ExamSleepPlanRequest(
        Long eventId,
        @NotNull PreparationState preparationState,
        @NotNull ExamNature examNature
) {
}
