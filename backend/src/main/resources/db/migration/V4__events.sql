-- Events 도메인: 일회성 일정 (매주 반복 수업은 Timetable 도메인)

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

CREATE INDEX ix_events_user_start ON events(user_id, start_at);
