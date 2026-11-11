package com.team11.backend.timetable.dto;

import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.TimetableEntry;
import com.team11.backend.common.domain.Source;
import java.time.LocalTime;

public record TimetableEntryView(
        Long entryId, String semester, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
        String courseName, String location, Source source, String externalId
) {

    public static TimetableEntryView from(TimetableEntry entry) {
        return new TimetableEntryView(entry.getId(), entry.getSemester(), entry.getDayOfWeek(), entry.getStartTime(),
                entry.getEndTime(), entry.getCourseName(), entry.getLocation(), entry.getSource(),
                entry.getExternalId());
    }
}
