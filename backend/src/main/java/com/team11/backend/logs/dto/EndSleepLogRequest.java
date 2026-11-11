package com.team11.backend.logs.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;

public record EndSleepLogRequest(
        OffsetDateTime at,
        @Min(1) @Max(5) Integer quality
) {
}
