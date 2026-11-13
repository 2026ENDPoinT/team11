package com.team11.backend.ai.dto;

import com.team11.backend.events.dto.EventView;
import com.team11.backend.logs.dto.SleepLogView;
import com.team11.backend.plans.domain.ExamNature;
import com.team11.backend.plans.domain.PreparationState;
import com.team11.backend.timetable.dto.SemesterListResponse.SemesterView;
import com.team11.backend.timetable.dto.TimetableEntryView;
import com.team11.backend.users.domain.Chronotype;
import java.time.OffsetDateTime;
import java.util.List;

/** AI-03 계약(`SPRING_HANDOFF.md`). 원시 스냅샷을 그대로 넘긴다(요약 금지). */
public record AiExamSleepRequest(
        String contractVersion, OffsetDateTime asOfAt, long sourceRevision, Long targetEventId,
        Selection selection, UserProfile userProfile,
        List<EventView> events, List<TimetableEntryView> timetableEntries, List<SemesterView> semesters,
        List<SleepLogView> sleepLogs
) {

    private static final String CONTRACT_VERSION = "SLEEPSYNC_EXAM_SNAPSHOT_V1";

    public static AiExamSleepRequest of(OffsetDateTime asOfAt, Long targetEventId, PreparationState preparationState,
                                         ExamNature examNature, UserProfile userProfile, List<EventView> events,
                                         List<TimetableEntryView> timetableEntries, List<SemesterView> semesters,
                                         List<SleepLogView> sleepLogs) {
        return new AiExamSleepRequest(CONTRACT_VERSION, asOfAt, 0L, targetEventId,
                new Selection(preparationState, examNature, asOfAt), userProfile, events, timetableEntries,
                semesters, sleepLogs);
    }

    public record Selection(PreparationState preparationState, ExamNature examNature, OffsetDateTime assessedAt) {
    }

    public record UserProfile(boolean onboardingDone, Integer prepMinEstimate, Settings settings) {
    }

    public record Settings(int sleepNeedMin, int sleepLatencyMin, int minSleepMin, int commuteMin, int prepMin,
                            int bufferMin, Chronotype chronotype) {
    }
}
