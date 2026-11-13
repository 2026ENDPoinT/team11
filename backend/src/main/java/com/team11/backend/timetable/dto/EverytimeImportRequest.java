package com.team11.backend.timetable.dto;

import jakarta.validation.constraints.NotBlank;

/** 에브리타임 "시간표 공유" 링크(https://everytime.kr/@...) */
public record EverytimeImportRequest(@NotBlank String url) {
}
