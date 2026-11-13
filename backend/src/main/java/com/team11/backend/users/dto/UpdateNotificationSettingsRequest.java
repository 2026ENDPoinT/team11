package com.team11.backend.users.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNotificationSettingsRequest(@NotNull Boolean wakeAlarmEnabled, @NotNull Boolean bedtimeAlarmEnabled,
                                                @NotNull Integer bedtimeLeadMin) {
}
