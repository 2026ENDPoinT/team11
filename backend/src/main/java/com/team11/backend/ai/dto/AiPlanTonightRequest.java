package com.team11.backend.ai.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** AI-02 `plan/tonight` 요청(AI-02_BACKEND_CONTRACT.md). 최근 35일 수면·전날/기상일 시간표·일정 전체를 그대로 넘긴다. */
public record AiPlanTonightRequest(
        OffsetDateTime now, LocalDate targetDate, Profile profile,
        List<AiSnapshot.SleepLog> sleepHistory, List<AiSnapshot.TimetableEntry> timetable,
        List<AiSnapshot.Event> events,
        boolean examNextDay // 호환 필드. AI-02는 시험 가산을 적용하지 않으므로 항상 false
) {

    public record Profile(int sleepNeedMin, int sleepLatencyMin, int commuteMin, int prepMin, int bufferMin,
                           int minSleepMin) {
    }
}
