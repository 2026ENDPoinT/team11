package com.team11.backend.users.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingsRequest(
        @NotNull Boolean enabled,

        @NotNull @Min(0) @Max(180)
        Integer windDownMinBefore,

        @NotNull Boolean bedReminderEnabled,
        @NotNull Boolean caffeineCutoffEnabled,
        @NotNull Boolean napReminderEnabled,
        @NotNull Boolean streakRiskEnabled,
        @NotNull Boolean weeklyReportEnabled,

        @Valid QuietHours quietHours // null이면 해제
) {

    public record QuietHours(@NotBlank String start, @NotBlank String end) {
    }
}
