package com.team11.backend.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateExamPeriodRequest(
        @NotBlank @Size(max = 30, message = "1~30자여야 해요.")
        String name,

        @NotNull LocalDate from,
        @NotNull LocalDate to,

        @NotEmpty @Valid
        List<ExamEntry> exams,

        @Valid
        Constraints constraints
) {

    public record ExamEntry(
            @NotNull Long eventId,
            @Min(1) @Max(5) int difficulty,
            @Min(0) @Max(100) int studyHoursNeeded
    ) {
    }

    public record Constraints(
            @Min(1) @Max(16) Integer dailyStudyHoursMax,
            Integer minSleepMin,
            Boolean includeNaps
    ) {

        public boolean includeNapsOrDefault() {
            return includeNaps == null || includeNaps;
        }
    }
}
