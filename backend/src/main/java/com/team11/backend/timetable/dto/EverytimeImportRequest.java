package com.team11.backend.timetable.dto;

import jakarta.validation.constraints.NotBlank;

public record EverytimeImportRequest(@NotBlank String url) {
}
