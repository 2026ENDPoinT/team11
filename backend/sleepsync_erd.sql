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
--                   Plans (plans) — V5
--                   Logs (sleep_logs, prep_logs) — V6
--                   Devices / Notifications (devices, notifications) — V7
-- Streak/Home(스트릭, 일별 결과, 홈 요약)은 기존 SleepLog/Plan 데이터를 그때그때 롤업해서
-- 계산하며 별도 테이블이 없어 이 파일에 추가 항목 없음(CLAUDE.md §6/§10, home 패키지 참고).
-- 나머지 도메인(Exam, Stats, Tips, Sync)은 전부 2차(CLAUDE.md §10),
-- Calendar(월간/하루 요약)는 MVP인데 누락된 상태로 다음 작업 예정.

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

-- V5__plans.sql (slots는 PLAN-05 생길 때 추가) ---------------------------

CREATE TABLE plans (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_date              DATE        NOT NULL,
    mode                     VARCHAR(10) NOT NULL,
    status                   VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    recommended_bed_at       TIMESTAMPTZ NOT NULL,
    recommended_wake_at      TIMESTAMPTZ NOT NULL,
    user_bed_at              TIMESTAMPTZ,
    sleep_min                INT         NOT NULL,
    breakdown_commute_min    INT,
    breakdown_prep_min       INT,
    breakdown_buffer_min     INT,
    breakdown_sleep_need_min INT,
    breakdown_sleep_latency_min INT,
    first_event_kind         VARCHAR(10),
    first_event_title        VARCHAR(100),
    first_event_start_at     TIMESTAMPTZ,
    reasons                  TEXT[]      NOT NULL DEFAULT '{}',
    warnings                 TEXT[]      NOT NULL DEFAULT '{}',
    generated_by             VARCHAR(10) NOT NULL,
    model_version            VARCHAR(50),
    generated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- V6__logs.sql ------------------------------------------------------------

CREATE TABLE sleep_logs (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date         DATE,
    bed_at       TIMESTAMPTZ NOT NULL,
    wake_at      TIMESTAMPTZ,
    duration_min INT,
    quality      INT,
    status       VARCHAR(10) NOT NULL,
    source       VARCHAR(10) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sleep_logs_time_range CHECK (wake_at IS NULL OR wake_at > bed_at),
    CONSTRAINT ck_sleep_logs_quality CHECK (quality IS NULL OR quality BETWEEN 1 AND 5)
);

CREATE TABLE prep_logs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date          DATE        NOT NULL,
    wake_at       TIMESTAMPTZ NOT NULL,
    departure_at  TIMESTAMPTZ NOT NULL,
    prep_min      INT         NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_prep_logs_user_date UNIQUE (user_id, date),
    CONSTRAINT ck_prep_logs_time_range CHECK (departure_at > wake_at)
);

-- V7__devices_notifications.sql --------------------------------------------

CREATE TABLE devices (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform       VARCHAR(10)  NOT NULL,
    push_token     VARCHAR(255) NOT NULL,
    app_version    VARCHAR(20),
    timezone       VARCHAR(50)  NOT NULL DEFAULT 'Asia/Seoul',
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_active_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_devices_push_token UNIQUE (push_token)
);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(20) NOT NULL,
    title      VARCHAR(100) NOT NULL,
    body       VARCHAR(500) NOT NULL,
    data       TEXT,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- Part 2. 인덱스
-- =====================================================================

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX ix_timetable_entries_user_semester ON timetable_entries(user_id, semester);
CREATE INDEX ix_events_user_start ON events(user_id, start_at);
CREATE INDEX ix_devices_user_active ON devices(user_id, active);
CREATE INDEX ix_notifications_user_id_desc ON notifications(user_id, id DESC);
CREATE UNIQUE INDEX uq_plans_active_per_date ON plans(user_id, target_date) WHERE status = 'ACTIVE';
CREATE INDEX ix_sleep_logs_user_date ON sleep_logs(user_id, date);
