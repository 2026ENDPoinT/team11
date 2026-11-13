package com.team11.backend.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** AI-03 응답. 해시·diagnostics·comparison·workload 등은 매핑하지 않는다(모르는 필드는 무시됨). */
public record AiExamSleepResult(Result result, String modelVersion) {

    public record Result(
            String status, // READY / BASELINE / TRADEOFF / NO_FEASIBLE_PLAN
            PlanOption selected, PlanOption alternative,
            List<String> reasons, List<String> warnings
    ) {
    }

    public record PlanOption(String strategy, List<TimelineEntry> timeline, Totals totals) {
    }

    /** kind: MAIN_SLEEP / NAP / STUDY_AVAILABLE / FIXED / WAKE_BUFFER / REST */
    public record TimelineEntry(String kind, OffsetDateTime startAt, OffsetDateTime endAt) {
    }

    public record Totals(double sleepOpportunityMin, double studyAvailableMin, double fixedMin,
                          double wakeBufferMin, double restMin) {
    }
}
