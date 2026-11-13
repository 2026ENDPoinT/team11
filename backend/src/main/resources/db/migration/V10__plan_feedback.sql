-- PLAN-06 플랜 피드백. 플랜 하나당 한 건(덮어쓰기)이라 plan_id를 PK로 둔다.

CREATE TABLE plan_feedbacks (
    plan_id    BIGINT PRIMARY KEY REFERENCES plans(id) ON DELETE CASCADE,
    rating     VARCHAR(10) NOT NULL,
    followed   BOOLEAN,
    reason     VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
