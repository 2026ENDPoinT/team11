package com.team11.backend.timetable.dto;

import java.time.LocalDate;
import java.util.List;

public record SemesterListResponse(List<SemesterView> semesters) {

    public record SemesterView(String semester, LocalDate startDate, LocalDate endDate, boolean current) {
    }
}
