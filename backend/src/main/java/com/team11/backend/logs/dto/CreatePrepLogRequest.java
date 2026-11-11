package com.team11.backend.logs.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CreatePrepLogRequest(
        @NotNull LocalDate date,
        @NotNull OffsetDateTime wakeAt,
        @NotNull OffsetDateTime departureAt
) {
}
