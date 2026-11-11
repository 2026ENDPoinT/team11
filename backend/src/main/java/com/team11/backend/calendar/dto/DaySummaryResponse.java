package com.team11.backend.calendar.dto;

import com.team11.backend.events.dto.EventView;
import com.team11.backend.logs.dto.PrepLogView;
import com.team11.backend.logs.dto.SleepLogView;
import com.team11.backend.plans.dto.PlanView;
import com.team11.backend.timetable.dto.TimetableEntryView;
import java.time.LocalDate;
import java.util.List;

public record DaySummaryResponse(LocalDate date, List<TimetableEntryView> classes, List<EventView> events,
                                  PlanView plan, SleepLogView sleepLog, PrepLogView prepLog,
                                  List<Object> conditionLogs) {
}
