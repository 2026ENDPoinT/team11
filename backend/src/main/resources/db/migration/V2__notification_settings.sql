-- Users 도메인: 알림 설정 (1:1, 없으면 Spring이 첫 조회/수정 시 기본값으로 생성)

CREATE TABLE notification_settings (
    user_id                 BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    wind_down_min_before    INT     NOT NULL DEFAULT 60,
    bed_reminder_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    caffeine_cutoff_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    nap_reminder_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    streak_risk_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    weekly_report_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_start       TIME,
    quiet_hours_end         TIME,
    CONSTRAINT ck_notification_settings_wind_down_min_before CHECK (wind_down_min_before BETWEEN 0 AND 180)
);
