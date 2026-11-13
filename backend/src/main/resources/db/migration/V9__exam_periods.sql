-- Exam 도메인: 시험기간은 사용자가 직접 등록(EXAM-01 자동 감지는 2차로 미룸).
-- 수면 플랜 생성(EXAM-07)은 AI 서버 연동 전이라 이 마이그레이션은 등록/조회 범위만 다룬다.

CREATE TABLE exam_periods (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                   VARCHAR(30) NOT NULL,
    from_date              DATE        NOT NULL,
    to_date                DATE        NOT NULL,
    daily_study_hours_max  SMALLINT,
    min_sleep_min          INT         NOT NULL,
    include_naps           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_exam_periods_date_range CHECK (to_date >= from_date AND to_date - from_date <= 30),
    CONSTRAINT ck_exam_periods_daily_study_hours_max CHECK (daily_study_hours_max BETWEEN 1 AND 16),
    CONSTRAINT ck_exam_periods_min_sleep_min CHECK (min_sleep_min >= 240)
);

CREATE INDEX ix_exam_periods_user ON exam_periods(user_id, from_date);

CREATE TABLE exam_period_exams (
    id                  BIGSERIAL PRIMARY KEY,
    exam_period_id      BIGINT NOT NULL REFERENCES exam_periods(id) ON DELETE CASCADE,
    event_id            BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    difficulty          SMALLINT NOT NULL CHECK (difficulty BETWEEN 1 AND 5),
    study_hours_needed  SMALLINT NOT NULL CHECK (study_hours_needed BETWEEN 0 AND 100),
    CONSTRAINT uq_exam_period_exams UNIQUE (exam_period_id, event_id)
);
