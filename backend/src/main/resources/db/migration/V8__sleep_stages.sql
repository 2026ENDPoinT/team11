CREATE TABLE sleep_stages (
    id           BIGSERIAL PRIMARY KEY,
    sleep_log_id BIGINT      NOT NULL REFERENCES sleep_logs(id) ON DELETE CASCADE,
    stage        VARCHAR(10) NOT NULL,
    start_at     TIMESTAMPTZ NOT NULL,
    end_at       TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sleep_stages_time_range CHECK (end_at > start_at)
);

CREATE INDEX ix_sleep_stages_sleep_log_id ON sleep_stages(sleep_log_id, start_at);
