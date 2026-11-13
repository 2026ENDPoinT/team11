package com.team11.backend.plans.dto;

import com.team11.backend.ai.dto.AiExamSleepResult;
import java.time.OffsetDateTime;
import java.util.List;

/** EXAM-08 응답. AI 원본 응답에서 프론트에 필요한 것만 추려 둔다(해시·diagnostics·evidence 등은 뺀다). */
public record ExamSleepPlanView(
        String status, PlanOption selected, PlanOption alternative,
        List<String> reasons, List<String> warnings, String modelVersion
) {

    public record PlanOption(String strategy, List<TimelineEntry> timeline, Totals totals) {

        static PlanOption from(AiExamSleepResult.PlanOption option) {
            if (option == null) {
                return null;
            }
            List<TimelineEntry> timeline = option.timeline().stream().map(TimelineEntry::from).toList();
            return new PlanOption(option.strategy(), timeline, Totals.from(option.totals()));
        }
    }

    public record TimelineEntry(String kind, OffsetDateTime startAt, OffsetDateTime endAt) {

        static TimelineEntry from(AiExamSleepResult.TimelineEntry entry) {
            return new TimelineEntry(entry.kind(), entry.startAt(), entry.endAt());
        }
    }

    public record Totals(double sleepOpportunityMin, double studyAvailableMin, double fixedMin, double wakeBufferMin,
                          double restMin) {

        static Totals from(AiExamSleepResult.Totals totals) {
            return new Totals(totals.sleepOpportunityMin(), totals.studyAvailableMin(), totals.fixedMin(),
                    totals.wakeBufferMin(), totals.restMin());
        }
    }

    public static ExamSleepPlanView from(AiExamSleepResult result) {
        AiExamSleepResult.Result r = result.result();
        return new ExamSleepPlanView(r.status(), PlanOption.from(r.selected()), PlanOption.from(r.alternative()),
                r.reasons(), r.warnings(), result.modelVersion());
    }
}
