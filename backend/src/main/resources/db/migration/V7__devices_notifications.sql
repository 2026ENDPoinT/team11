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

CREATE INDEX ix_devices_user_active ON devices(user_id, active);

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

CREATE INDEX ix_notifications_user_id_desc ON notifications(user_id, id DESC);
