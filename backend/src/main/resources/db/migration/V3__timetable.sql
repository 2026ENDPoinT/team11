-- Timetable 도메인: 매주 반복되는 수업 (일회성 일정은 Events 도메인에서 다룸)

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

CREATE INDEX ix_timetable_entries_user_semester ON timetable_entries(user_id, semester);
