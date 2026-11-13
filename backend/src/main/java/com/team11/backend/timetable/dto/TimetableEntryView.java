package com.team11.backend.timetable.dto;

import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.timetable.domain.Source;
import com.team11.backend.timetable.domain.TimetableEntry;
import java.time.LocalTime;

public record TimetableEntryView(Long entryId, String semester, DayOfWeek dayOfWeek, LocalTime startTime,
                                  LocalTime endTime, String courseName, String room, Source source) {

    public static TimetableEntryView from(TimetableEntry e) {
        return new TimetableEntryView(e.getId(), e.getSemester(), e.getDayOfWeek(), e.getStartTime(), e.getEndTime(),
                e.getCourseName(), e.getRoom(), e.getSource());
    }
}
