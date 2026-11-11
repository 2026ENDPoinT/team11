-- Auth 도메인 초기 스키마: users, characters, consents, refresh_tokens
-- 전체 ERD(sleepsync_erd.sql, 31개 테이블)는 추후 도메인별로 이어서 마이그레이션 추가.

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

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens(user_id);

INSERT INTO characters (code, name, description, sort_order) VALUES
    ('MOON_BUNNY', '달토끼', '밤마다 같이 잠드는 친구', 0),
    ('SLEEPY_BEAR', '졸린 곰', '느긋하게 푹 자는 친구', 1);
