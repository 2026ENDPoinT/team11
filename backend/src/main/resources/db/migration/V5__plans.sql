-- Plans 도메인: 하루 단위 수면 플랜. 슬롯(plan_slots)은 PLAN-05(2차)가 생길 때 추가.

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

CREATE UNIQUE INDEX uq_plans_active_per_date ON plans(user_id, target_date) WHERE status = 'ACTIVE';
