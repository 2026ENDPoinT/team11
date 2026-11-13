package com.team11.backend.logs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 4-2 수면 기록 입력: 취침/기상/컨디션(1 나쁨 · 2 보통 · 3 좋음)/메모. 생성/PUT 공용. */
public record SleepLogRequest(
        @NotNull OffsetDateTime bedAt,
        @NotNull OffsetDateTime wakeAt,
        @Min(1) @Max(3) Integer quality,
        @Size(max = 200, message = "200자 이내여야 해요.") String memo
) {
}
