package com.team11.backend.timetable.dto;

import com.team11.backend.timetable.domain.DayOfWeek;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record UpdateTimetableEntryRequest(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,

        @Size(min = 1, max = 50, message = "1~50자여야 해요.")
        String courseName,

        @Size(max = 30, message = "30자 이내여야 해요.")
        String location
) {

    public boolean changesReadonlyFields() {
        return dayOfWeek != null || startTime != null || endTime != null || courseName != null;
    }
}
