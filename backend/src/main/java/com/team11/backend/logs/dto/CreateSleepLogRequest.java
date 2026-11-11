package com.team11.backend.logs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CreateSleepLogRequest(
        @NotNull OffsetDateTime bedAt,
        @NotNull OffsetDateTime wakeAt,
        @Min(1) @Max(5) Integer quality
) {
}
