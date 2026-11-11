package com.team11.backend.timetable.dto;

import com.team11.backend.timetable.domain.DayOfWeek;
import com.team11.backend.common.domain.Source;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

public record BulkReplaceTimetableRequest(
        @NotNull Source source,

        @NotNull @Size(max = 100, message = "한 번에 최대 100개까지예요.")
        List<@Valid Entry> entries
) {

    public record Entry(
            String externalId,
            @NotNull DayOfWeek dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,

            @NotBlank @Size(min = 1, max = 50, message = "1~50자여야 해요.")
            String courseName,

            @Size(max = 30, message = "30자 이내여야 해요.")
            String location
    ) {
    }
}
