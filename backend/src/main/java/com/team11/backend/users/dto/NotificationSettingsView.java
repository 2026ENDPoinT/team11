package com.team11.backend.users.dto;

import com.team11.backend.users.domain.NotificationSettings;
import java.time.format.DateTimeFormatter;

public record NotificationSettingsView(
        boolean enabled, int windDownMinBefore, boolean bedReminderEnabled, boolean caffeineCutoffEnabled,
        boolean napReminderEnabled, boolean streakRiskEnabled, boolean weeklyReportEnabled, QuietHours quietHours
) {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    public record QuietHours(String start, String end) {
    }

    public static NotificationSettingsView from(NotificationSettings settings) {
        QuietHours quietHours = settings.getQuietHoursStart() == null ? null
                : new QuietHours(settings.getQuietHoursStart().format(HH_MM), settings.getQuietHoursEnd().format(HH_MM));
        return new NotificationSettingsView(
                settings.isEnabled(), settings.getWindDownMinBefore(), settings.isBedReminderEnabled(),
                settings.isCaffeineCutoffEnabled(), settings.isNapReminderEnabled(), settings.isStreakRiskEnabled(),
                settings.isWeeklyReportEnabled(), quietHours);
    }
}
