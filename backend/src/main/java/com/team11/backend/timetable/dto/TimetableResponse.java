package com.team11.backend.timetable.dto;

import java.util.List;

public record TimetableResponse(String semester, List<TimetableEntryView> entries) {
}
