package com.team11.backend.users.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor
public class NotificationSettings {

    @Id
    private Long userId;

    private boolean enabled = true;
    private int windDownMinBefore = 60;
    private boolean bedReminderEnabled = true;
    private boolean caffeineCutoffEnabled = true;
    private boolean napReminderEnabled = true;
    private boolean streakRiskEnabled = true;
    private boolean weeklyReportEnabled = true;
    private LocalTime quietHoursStart = LocalTime.of(0, 30);
    private LocalTime quietHoursEnd = LocalTime.of(7, 0);

    public NotificationSettings(Long userId) {
        this.userId = userId;
    }

    public void update(boolean enabled, int windDownMinBefore, boolean bedReminderEnabled,
                        boolean caffeineCutoffEnabled, boolean napReminderEnabled,
                        boolean streakRiskEnabled, boolean weeklyReportEnabled,
                        LocalTime quietHoursStart, LocalTime quietHoursEnd) {
        this.enabled = enabled;
        this.windDownMinBefore = windDownMinBefore;
        this.bedReminderEnabled = bedReminderEnabled;
        this.caffeineCutoffEnabled = caffeineCutoffEnabled;
        this.napReminderEnabled = napReminderEnabled;
        this.streakRiskEnabled = streakRiskEnabled;
        this.weeklyReportEnabled = weeklyReportEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
    }
}
