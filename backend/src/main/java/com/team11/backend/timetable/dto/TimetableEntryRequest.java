package com.team11.backend.timetable.dto;

import com.team11.backend.timetable.domain.DayOfWeek;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/** 3-1 수업 추가/수정 시트: 과목명·요일·시작·종료·강의실. 생성/PUT 공용. */
public record TimetableEntryRequest(
        @NotBlank String semester,
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotBlank @Size(max = 50, message = "50자 이내여야 해요.") String courseName,
        @Size(max = 50, message = "50자 이내여야 해요.") String room
) {
}
