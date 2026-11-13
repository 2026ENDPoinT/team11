package com.team11.backend.exam.dto;

import com.team11.backend.events.domain.Event;
import com.team11.backend.exam.domain.ExamPeriod;
import com.team11.backend.exam.domain.ExamPeriodExam;
import com.team11.backend.exam.domain.ExamPeriodStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ExamPeriodView(
        Long examPeriodId, String name, LocalDate from, LocalDate to, List<ExamItemView> exams,
        ConstraintsView constraints, PlanSummaryView planSummary, Instant createdAt
) {

    /** AI 서버(EXAM-07) 연동 전이라 플랜은 항상 생성되지 않은 상태로 둔다. */
    private static final PlanSummaryView NO_PLAN_YET = new PlanSummaryView(false, 0, null, false);

    public static ExamPeriodView of(ExamPeriod examPeriod, Map<Long, Event> eventsById) {
        List<ExamItemView> items = examPeriod.getExams().stream()
                .map(exam -> ExamItemView.of(exam, eventsById.get(exam.getEventId())))
                .toList();
        return new ExamPeriodView(examPeriod.getId(), examPeriod.getName(), examPeriod.getFromDate(),
                examPeriod.getToDate(), items,
                new ConstraintsView(examPeriod.getDailyStudyHoursMax(), examPeriod.getMinSleepMin(),
                        examPeriod.isIncludeNaps()),
                NO_PLAN_YET, examPeriod.getCreatedAt());
    }

    public record ExamItemView(Long eventId, String title, OffsetDateTime startAt, int difficulty,
                                int studyHoursNeeded) {

        static ExamItemView of(ExamPeriodExam exam, Event event) {
            return new ExamItemView(exam.getEventId(), event.getTitle(), event.getStartAt(), exam.getDifficulty(),
                    exam.getStudyHoursNeeded());
        }
    }

    public record ConstraintsView(Integer dailyStudyHoursMax, int minSleepMin, boolean includeNaps) {
    }

    public record PlanSummaryView(boolean generated, int planCount, Instant lastGeneratedAt, boolean stale) {
    }

    public record Summary(Long examPeriodId, String name, LocalDate from, LocalDate to, int examCount,
                           ExamPeriodStatus status, boolean planGenerated) {

        public static Summary of(ExamPeriod examPeriod, LocalDate today) {
            ExamPeriodStatus status;
            if (today.isBefore(examPeriod.getFromDate())) {
                status = ExamPeriodStatus.UPCOMING;
            } else if (today.isAfter(examPeriod.getToDate())) {
                status = ExamPeriodStatus.PAST;
            } else {
                status = ExamPeriodStatus.ONGOING;
            }
            return new Summary(examPeriod.getId(), examPeriod.getName(), examPeriod.getFromDate(),
                    examPeriod.getToDate(), examPeriod.getExams().size(), status, false);
        }
    }
}
