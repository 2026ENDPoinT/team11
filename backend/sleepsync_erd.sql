-- SleepSync DB 스냅샷. 적용된 Flyway 마이그레이션(src/main/resources/db/migration)을 그대로 반영한다.
-- 마이그레이션을 추가/수정할 때 이 파일도 같이 갱신한다. 현재: V1__init.sql (2026-11-14 전면 재작성)

-- SleepSync 백엔드 v2 초기 스키마 (와이어프레임 v2 + AI 서버 1차 완성본 기준으로 전면 재작성).
-- 이 파일은 재작성 기간에만 편집한다. 이후 스키마 변경은 V2__ 이상 새 파일로만.

CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    login_id              VARCHAR(20)  NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    name                  VARCHAR(30)  NOT NULL,
    onboarding_done       BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 수면 설정 시트(일상 모드 입력) — 범위는 AI-02 profile 계약과 동일
    sleep_need_min        INT          NOT NULL DEFAULT 465,
    sleep_latency_min     INT          NOT NULL DEFAULT 15,
    commute_min           INT          NOT NULL DEFAULT 0,
    prep_min              INT          NOT NULL DEFAULT 0,
    buffer_min            INT          NOT NULL DEFAULT 10,
    min_sleep_min         INT          NOT NULL DEFAULT 300,
    -- 시험 설정(온보딩): 시험 며칠 전부터 시험 모드를 권할지
    exam_prep_days        INT          NOT NULL DEFAULT 3,
    -- 집중 모드 하루 필요 수면 범위(침대 체류 분) — AI-06 minInBedMinutes/maxInBedMinutes
    focus_min_in_bed_min  INT          NOT NULL DEFAULT 360,
    focus_max_in_bed_min  INT          NOT NULL DEFAULT 480,
    failed_login_count    INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_login_id UNIQUE (login_id),
    CONSTRAINT ck_users_sleep_need_min CHECK (sleep_need_min BETWEEN 300 AND 600),
    CONSTRAINT ck_users_sleep_latency_min CHECK (sleep_latency_min BETWEEN 0 AND 60),
    CONSTRAINT ck_users_commute_min CHECK (commute_min BETWEEN 0 AND 240),
    CONSTRAINT ck_users_prep_min CHECK (prep_min BETWEEN 0 AND 240),
    CONSTRAINT ck_users_buffer_min CHECK (buffer_min BETWEEN 0 AND 60),
    CONSTRAINT ck_users_min_sleep_min CHECK (min_sleep_min BETWEEN 240 AND 480),
    CONSTRAINT ck_users_exam_prep_days CHECK (exam_prep_days BETWEEN 1 AND 14),
    CONSTRAINT ck_users_focus_range CHECK (focus_min_in_bed_min BETWEEN 1 AND 960
                                           AND focus_max_in_bed_min BETWEEN 1 AND 960
                                           AND focus_min_in_bed_min <= focus_max_in_bed_min)
);

-- 온보딩 0-3 동의 화면: MEDICAL_DISCLAIMER / TERMS / PRIVACY(필수 3) + NOTIFICATION(선택)
CREATE TABLE consents (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(30) NOT NULL,
    agreed     BOOLEAN     NOT NULL DEFAULT FALSE,
    agreed_at  TIMESTAMPTZ,
    CONSTRAINT uq_consents_user_type UNIQUE (user_id, type)
);

-- 설정 5-2 알림 화면: 기상 알람 / 취침 알람 / 취침 알람 미리(15·30·60분)
CREATE TABLE notification_settings (
    user_id                BIGINT  PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    wake_alarm_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    bedtime_alarm_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    bedtime_lead_min       INT     NOT NULL DEFAULT 30,
    CONSTRAINT ck_notification_settings_lead CHECK (bedtime_lead_min IN (15, 30, 60))
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
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);

-- 일정 3-1 시간표: 과목명·요일·시작·종료·강의실. source = MANUAL | EVERYTIME
CREATE TABLE timetable_entries (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    semester     VARCHAR(10)  NOT NULL,
    day_of_week  VARCHAR(3)   NOT NULL,
    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,
    course_name  VARCHAR(50)  NOT NULL,
    room         VARCHAR(50),
    source       VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    external_id  VARCHAR(50),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_timetable_entries_time_range CHECK (start_time < end_time)
);
CREATE INDEX ix_timetable_entries_user_semester ON timetable_entries(user_id, semester);

-- 일정 3-3 일정: 과제/시험/약속/기타. 종일·다일(start~end가 여러 날) 지원. end_at NULL = 시작 시각만 있는 일정
CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(100) NOT NULL,
    type        VARCHAR(10)  NOT NULL,
    start_at    TIMESTAMPTZ  NOT NULL,
    end_at      TIMESTAMPTZ,
    all_day     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_events_time_range CHECK (end_at IS NULL OR end_at > start_at)
);
CREATE INDEX ix_events_user_start ON events(user_id, start_at);

-- 리포트 4-2 수면 기록(수동 입력 전용): 취침/기상/컨디션 3단계/메모. date = KST 기상일, 하루 1건
CREATE TABLE sleep_logs (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date          DATE        NOT NULL,
    bed_at        TIMESTAMPTZ NOT NULL,
    wake_at       TIMESTAMPTZ NOT NULL,
    duration_min  INT         NOT NULL,
    quality       INT,
    memo          VARCHAR(200),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sleep_logs_user_date UNIQUE (user_id, date),
    CONSTRAINT ck_sleep_logs_time_range CHECK (wake_at > bed_at),
    CONSTRAINT ck_sleep_logs_quality CHECK (quality IS NULL OR quality BETWEEN 1 AND 3)
);

-- 홈 1 추천 플랜: 기상일(date) × 모드(NORMAL/FOCUS/EXAM)당 1건. 시험 모드가 NO_FEASIBLE_PLAN이면 bed/wake가 비어 nullable
CREATE TABLE plans (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date           DATE         NOT NULL,
    mode           VARCHAR(10)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    bed_at         TIMESTAMPTZ,
    wake_at        TIMESTAMPTZ,
    sleep_min      INT,
    basis_text     VARCHAR(120),
    generated_by   VARCHAR(10)  NOT NULL,
    model_version  VARCHAR(50),
    reasons        TEXT[]       NOT NULL DEFAULT '{}',
    warnings       TEXT[]       NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_plans_user_date_mode UNIQUE (user_id, date, mode)
);
