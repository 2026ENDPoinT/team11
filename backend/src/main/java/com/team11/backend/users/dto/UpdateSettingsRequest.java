package com.team11.backend.users.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.stream.Stream;

/** 수면 설정 시트는 항목 하나씩 고치므로 보낸 필드만 반영한다(null = 유지). */
public record UpdateSettingsRequest(
        @Min(300) @Max(600) Integer sleepNeedMin,
        @Min(0) @Max(60) Integer sleepLatencyMin,
        @Min(0) @Max(240) Integer commuteMin,
        @Min(0) @Max(240) Integer prepMin,
        @Min(0) @Max(60) Integer bufferMin,
        @Min(240) @Max(480) Integer minSleepMin,
        @Min(1) @Max(14) Integer examPrepDays,
        @Min(1) @Max(960) Integer focusMinInBedMin,
        @Min(1) @Max(960) Integer focusMaxInBedMin
) {

    public boolean isEmpty() {
        return Stream.of(sleepNeedMin, sleepLatencyMin, commuteMin, prepMin, bufferMin, minSleepMin, examPrepDays,
                focusMinInBedMin, focusMaxInBedMin).allMatch(v -> v == null);
    }
}
