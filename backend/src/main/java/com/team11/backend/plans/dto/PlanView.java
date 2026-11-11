package com.team11.backend.plans.dto;

import com.team11.backend.plans.domain.FirstEventKind;
import com.team11.backend.plans.domain.Plan;
import com.team11.backend.plans.domain.PlanMode;
import com.team11.backend.plans.domain.PlanStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PlanView(
        Long planId, LocalDate targetDate, PlanMode mode, PlanStatus status, FirstEventView firstEvent,
        OffsetDateTime recommendedWakeAt, OffsetDateTime recommendedBedAt, OffsetDateTime userBedAt, int sleepMin,
        Breakdown breakdown, List<String> reasons, List<String> warnings, List<Object> slots,
        String generatedBy, String modelVersion, OffsetDateTime generatedAt
) {

    public record FirstEventView(FirstEventKind kind, String title, OffsetDateTime startAt) {
    }

    public record Breakdown(int commuteMin, int prepMin, int bufferMin, int sleepNeedMin, int sleepLatencyMin) {
    }

    public static PlanView from(Plan plan) {
        FirstEventView firstEvent = plan.getFirstEventKind() == null ? null
                : new FirstEventView(plan.getFirstEventKind(), plan.getFirstEventTitle(), plan.getFirstEventStartAt());
        Breakdown breakdown = plan.getBreakdownSleepNeedMin() == null ? null
                : new Breakdown(plan.getBreakdownCommuteMin(), plan.getBreakdownPrepMin(), plan.getBreakdownBufferMin(),
                        plan.getBreakdownSleepNeedMin(), plan.getBreakdownSleepLatencyMin());
        return new PlanView(plan.getId(), plan.getTargetDate(), plan.getMode(), plan.getStatus(), firstEvent,
                plan.getRecommendedWakeAt(), plan.getRecommendedBedAt(), plan.getUserBedAt(), plan.getSleepMin(),
                breakdown, plan.getReasons(), plan.getWarnings(), List.of(),
                plan.getGeneratedBy(), plan.getModelVersion(), plan.getGeneratedAt());
    }
}
