package com.team11.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.team11.backend.plans.domain.ExamNature;
import com.team11.backend.plans.domain.PreparationState;
import java.time.OffsetDateTime;
import java.util.List;

/** AI-03 `plan/exam-sleep` Spring 스냅샷 계약(SLEEPSYNC_EXAM_SNAPSHOT_V1, SPRING_HANDOFF.md). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiExamSleepRequest(
        String contractVersion, OffsetDateTime asOfAt, long sourceRevision,
        Long targetEventId, // null이면 생략 → AI가 72시간 내 가장 가까운 시험을 고른다
        Selection selection, UserProfile userProfile,
        List<AiSnapshot.Event> events, List<AiSnapshot.TimetableEntry> timetableEntries,
        List<AiSnapshot.Semester> semesters, List<AiSnapshot.SleepLog> sleepLogs
) {

    public static final String CONTRACT_VERSION = "SLEEPSYNC_EXAM_SNAPSHOT_V1";

    public record Selection(PreparationState preparationState, ExamNature examNature, OffsetDateTime assessedAt) {
    }

    public record UserProfile(boolean onboardingDone, Settings settings) {
    }

    public record Settings(int sleepNeedMin, int sleepLatencyMin, int minSleepMin, int commuteMin, int prepMin,
                            int bufferMin) {
    }
}
