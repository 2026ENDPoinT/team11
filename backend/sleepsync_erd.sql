-- SleepSync DB 스키마 (source of truth)
--
-- 이 파일은 적용된 Flyway 마이그레이션(src/main/resources/db/migration)의 현재 상태를
-- 그대로 반영한 스냅샷이다. 마이그레이션을 추가/수정하면 이 파일도 같이 갱신한다.
-- ERD Cloud 다이어그램은 이 파일 기준으로 다시 그릴 예정이며, 그 전까지는 이 파일이
-- 1차 소스 오브 트루스다 (CLAUDE.md 1번 참고).
--
-- 구성: Part 1 = 테이블, Part 2 = 인덱스
-- 현재 반영된 도메인: Auth (users, characters, consents, refresh_tokens) — V1
--                   Users (notification_settings) — V2
--                   Timetable (timetable_entries) — V3
--                   Events (events) — V4
-- 나머지 도메인(Devices, Exam, Plans, Logs, Stats, Home, Tips, Sync)은
-- 구현 순서(CLAUDE.md 3번: Auth → Users → Timetable ...)에 맞춰 이어서 추가된다.

-- =====================================================================
-- Part 1. 테이블
-- =====================================================================

-- V1__init_auth.sql ---------------------------------------------------

CREATE TABLE characters (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(30)  NOT NULL,
    description VARCHAR(200),
    image_url   VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_characters_code UNIQUE (code)
);

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    login_id            VARCHAR(20)  NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    nickname            VARCHAR(12)  NOT NULL,
    onboarding_done     BOOLEAN      NOT NULL DEFAULT FALSE,
    chronotype          VARCHAR(10)  NOT NULL DEFAULT 'NEUTRAL',
    sleep_need_min      INT          NOT NULL DEFAULT 465,
    sleep_latency_min   INT          NOT NULL DEFAULT 15,
    commute_min         INT          NOT NULL DEFAULT 0,
    prep_min            INT          NOT NULL DEFAULT 0,
    buffer_min          INT          NOT NULL DEFAULT 10,
    min_sleep_min       INT          NOT NULL DEFAULT 300,
    character_id        BIGINT REFERENCES characters(id) ON DELETE SET NULL,
    failed_login_count  INT          NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_login_id UNIQUE (login_id),
    CONSTRAINT ck_users_sleep_need_min CHECK (sleep_need_min BETWEEN 300 AND 600),
    CONSTRAINT ck_users_sleep_latency_min CHECK (sleep_latency_min BETWEEN 0 AND 60),
    CONSTRAINT ck_users_commute_min CHECK (commute_min BETWEEN 0 AND 240),
    CONSTRAINT ck_users_prep_min CHECK (prep_min BETWEEN 0 AND 240),
    CONSTRAINT ck_users_buffer_min CHECK (buffer_min BETWEEN 0 AND 60),
    CONSTRAINT ck_users_min_sleep_min CHECK (min_sleep_min BETWEEN 240 AND 480)
);

CREATE TABLE consents (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(20)  NOT NULL,
    agreed     BOOLEAN      NOT NULL DEFAULT FALSE,
    agreed_at  TIMESTAMPTZ,
    CONSTRAINT uq_consents_user_type UNIQUE (user_id, type)
);

CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(128) NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  BIGINT REFERENCES refresh_tokens(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- V2__notification_settings.sql ----------------------------------------

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

-- V3__timetable.sql -----------------------------------------------------

CREATE TABLE timetable_entries (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    semester     VARCHAR(6)  NOT NULL,
    day_of_week  VARCHAR(3)  NOT NULL,
    start_time   TIME        NOT NULL,
    end_time     TIME        NOT NULL,
    course_name  VARCHAR(50) NOT NULL,
    location     VARCHAR(30),
    source       VARCHAR(10) NOT NULL DEFAULT 'MANUAL',
    external_id  VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_timetable_entries_time_range CHECK (start_time < end_time)
);

-- V4__events.sql --------------------------------------------------------

CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(20)  NOT NULL,
    title       VARCHAR(50)  NOT NULL,
    start_at    TIMESTAMPTZ  NOT NULL,
    end_at      TIMESTAMPTZ,
    all_day     BOOLEAN      NOT NULL DEFAULT FALSE,
    tag         VARCHAR(20),
    note        VARCHAR(200),
    source      VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    external_id VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_events_time_range CHECK (end_at IS NULL OR start_at < end_at)
);

-- =====================================================================
-- Part 2. 인덱스
-- =====================================================================

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX ix_timetable_entries_user_semester ON timetable_entries(user_id, semester);
CREATE INDEX ix_events_user_start ON events(user_id, start_at);
