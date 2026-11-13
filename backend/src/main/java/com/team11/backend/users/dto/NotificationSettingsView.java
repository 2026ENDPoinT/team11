package com.team11.backend.users.dto;

import com.team11.backend.users.domain.NotificationSettings;

public record NotificationSettingsView(boolean wakeAlarmEnabled, boolean bedtimeAlarmEnabled, int bedtimeLeadMin) {

    public static NotificationSettingsView from(NotificationSettings s) {
        return new NotificationSettingsView(s.isWakeAlarmEnabled(), s.isBedtimeAlarmEnabled(), s.getBedtimeLeadMin());
    }
}
