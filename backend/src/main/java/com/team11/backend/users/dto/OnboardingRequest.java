package com.team11.backend.users.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OnboardingRequest(
        @NotBlank String weekdayBedTime,
        @NotBlank String weekdayWakeTime,
        @NotBlank String freedayBedTime,
        @NotBlank String freedayWakeTime,

        @NotNull @Min(0) @Max(180) Integer sleepLatencyMin,
        @NotNull @Min(0) @Max(240) Integer commuteMin,
        @NotNull @Min(0) @Max(240) Integer prepMin,

        Integer caffeinePerDay // 카페인 로그 도메인이 아직 없어 받기만 하고 저장·사용하지 않음
) {
}
