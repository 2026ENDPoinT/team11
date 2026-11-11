package com.team11.backend.ai.dto;

import com.team11.backend.events.dto.EventView;
import com.team11.backend.timetable.dto.TimetableEntryView;
import com.team11.backend.users.domain.Chronotype;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Notion "12. 내부 AI 서버 API" AI-02 계약. 원시 스냅샷을 그대로 넘긴다(요약 금지). */
public record AiPlanTonightRequest(
        OffsetDateTime now, LocalDate targetDate, Profile profile,
        List<Object> sleepHistory, // Logs 도메인이 없어서 항상 []
        List<TimetableEntryView> timetable, List<EventView> events,
        boolean examNextDay // 호환 필드, 항상 false
) {

    public record Profile(Chronotype chronotype, int sleepNeedMin, int sleepLatencyMin, int commuteMin,
                           int prepMin, Integer prepMinEstimate, int bufferMin, int minSleepMin) {
    }
}
