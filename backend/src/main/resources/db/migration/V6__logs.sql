-- Logs 도메인: 수면 기록, 준비시간 기록 (낮잠/컨디션/카페인은 2차)

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

CREATE INDEX ix_sleep_logs_user_date ON sleep_logs(user_id, date);

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
