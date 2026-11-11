package com.team11.backend.calendar.dto;

import com.team11.backend.events.domain.EventTag;
import com.team11.backend.events.domain.EventType;
import com.team11.backend.plans.domain.PlanMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record MonthSummaryResponse(String month, List<DaySummary> days) {

    public record DaySummary(LocalDate date, int classCount, LocalTime firstClassTime, List<EventSummary> events,
                              PlanSummary plan, SleepSummary sleep) {
    }

    public record EventSummary(Long eventId, EventType type, String title, EventTag tag) {
    }

    public record PlanSummary(PlanMode mode, OffsetDateTime recommendedBedAt, OffsetDateTime recommendedWakeAt) {
    }

    public record SleepSummary(Integer durationMin, boolean goalMet) {
    }
}
