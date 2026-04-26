-- V4: gym, membership_plan, subscription, attendance
-- Also wires gym_id into the existing class_session table.

-- -------------------------------------------------------------------------
-- GYM
-- -------------------------------------------------------------------------
CREATE TABLE gym (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    address       VARCHAR(200) NOT NULL,
    city          VARCHAR(100) NOT NULL,
    phone         VARCHAR(15),
    opening_hours VARCHAR(200),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_gym_city ON gym(city);

-- Add gym FK to class_session (nullable so existing rows are unaffected)
ALTER TABLE class_session ADD COLUMN gym_id BIGINT REFERENCES gym(id) ON DELETE RESTRICT;
CREATE INDEX idx_class_session_gym_id ON class_session(gym_id);

-- -------------------------------------------------------------------------
-- MEMBERSHIP_PLAN
-- -------------------------------------------------------------------------
CREATE TABLE membership_plan (
    id                 BIGSERIAL      PRIMARY KEY,
    name               VARCHAR(100)   NOT NULL,
    description        TEXT,
    price_monthly      NUMERIC(10, 2) NOT NULL,
    classes_per_month  INTEGER,                   -- NULL = unlimited
    allows_waitlist    BOOLEAN        NOT NULL DEFAULT TRUE,
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- SUBSCRIPTION
-- -------------------------------------------------------------------------
CREATE TABLE subscription (
    id                      BIGSERIAL   PRIMARY KEY,
    user_id                 BIGINT      NOT NULL REFERENCES app_user(id)      ON DELETE RESTRICT,
    plan_id                 BIGINT      NOT NULL REFERENCES membership_plan(id) ON DELETE RESTRICT,
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),
    start_date              DATE        NOT NULL,
    renewal_date            DATE        NOT NULL,
    classes_used_this_month INTEGER     NOT NULL DEFAULT 0,
    version                 BIGINT      NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscription_user_id ON subscription(user_id);
CREATE INDEX idx_subscription_plan_id ON subscription(plan_id);
CREATE INDEX idx_subscription_status  ON subscription(status);

-- -------------------------------------------------------------------------
-- ATTENDANCE  (one record per user per session; booking_id may be null for
--              manually added records)
-- -------------------------------------------------------------------------
CREATE TABLE attendance (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user(id)      ON DELETE RESTRICT,
    session_id  BIGINT      NOT NULL REFERENCES class_session(id) ON DELETE RESTRICT,
    booking_id  BIGINT               REFERENCES booking(id)       ON DELETE SET NULL,
    status      VARCHAR(20) NOT NULL CHECK (status IN ('ATTENDED', 'NO_SHOW')),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, session_id)
);

CREATE INDEX idx_attendance_session_id ON attendance(session_id);
CREATE INDEX idx_attendance_user_id    ON attendance(user_id);
