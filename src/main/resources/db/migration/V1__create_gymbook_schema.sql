-- V1__create_gymbook_schema.sql
-- Complete GymBook schema — single consolidated init script.
-- ON DELETE defaults to RESTRICT except where noted.
-- Enums stored as text + CHECK for easy evolution.
-- All timestamps use TIMESTAMPTZ (always UTC, unambiguous across DST).
-- 'app_user' instead of 'user' because user is a reserved word in PostgreSQL.
-- Instructors are stored as app_user rows with role = 'INSTRUCTOR'.

-- -------------------------------------------------------------------------
-- CLASS_TYPE  (template — e.g. "Spinning 45 min")
-- -------------------------------------------------------------------------
CREATE TABLE class_type (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    level       VARCHAR(20)  NOT NULL
                    CHECK (level IN ('BASIC', 'INTERMEDIATE', 'ADVANCED')),
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- APP_USER  (customer / instructor / admin)
-- PII: email, name, phone — apply data-retention policy before going live.
-- specialty is set for users with role INSTRUCTOR.
-- -------------------------------------------------------------------------
CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    phone         VARCHAR(15),
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER'
                      CHECK (role IN ('CUSTOMER', 'INSTRUCTOR', 'ADMIN')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    specialty     VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_user_email ON app_user(email);

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

CREATE INDEX idx_gym_city        ON gym(city);
CREATE UNIQUE INDEX gym_name_lower_uq ON gym (lower(name));

-- -------------------------------------------------------------------------
-- CLASS_SESSION  (concrete scheduled occurrence of a class type)
-- version column supports JPA optimistic locking (@Version) to prevent
-- double-booking races without a SELECT … FOR UPDATE on the whole row.
-- instructor_id references app_user (role = INSTRUCTOR).
-- -------------------------------------------------------------------------
CREATE TABLE class_session (
    id               BIGSERIAL   PRIMARY KEY,
    start_time       TIMESTAMPTZ NOT NULL,
    duration_minutes INT         NOT NULL CHECK (duration_minutes > 0),
    max_capacity     INT         NOT NULL CHECK (max_capacity > 0),
    room             VARCHAR(50) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                         CHECK (status IN ('SCHEDULED', 'ACTIVE', 'CANCELLED', 'FINISHED')),
    class_type_id    BIGINT      NOT NULL REFERENCES class_type(id)  ON DELETE RESTRICT,
    instructor_id    BIGINT               REFERENCES app_user(id)    ON DELETE SET NULL,
    gym_id           BIGINT               REFERENCES gym(id)         ON DELETE RESTRICT,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_class_session_start_time ON class_session(start_time);
CREATE INDEX idx_class_session_status     ON class_session(status);
CREATE INDEX idx_class_session_gym_id     ON class_session(gym_id);

-- -------------------------------------------------------------------------
-- BOOKING
-- UNIQUE (user_id, session_id) prevents a user booking the same session twice.
-- -------------------------------------------------------------------------
CREATE TABLE booking (
    id         BIGSERIAL   PRIMARY KEY,
    booked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status     VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'
                   CHECK (status IN ('CONFIRMED', 'CANCELLED', 'ATTENDED', 'NO_SHOW')),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id)      ON DELETE RESTRICT,
    session_id BIGINT      NOT NULL REFERENCES class_session(id) ON DELETE RESTRICT,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, session_id)
);

CREATE INDEX idx_booking_user_id    ON booking(user_id);
CREATE INDEX idx_booking_session_id ON booking(session_id);
CREATE INDEX idx_booking_status     ON booking(status);

-- -------------------------------------------------------------------------
-- WAITLIST
-- position is the queue number; the service renumbers on promotion/removal.
-- -------------------------------------------------------------------------
CREATE TABLE waitlist (
    id         BIGSERIAL   PRIMARY KEY,
    position   INT         NOT NULL CHECK (position > 0),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id)      ON DELETE RESTRICT,
    session_id BIGINT      NOT NULL REFERENCES class_session(id) ON DELETE RESTRICT,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, session_id)
);

CREATE INDEX idx_waitlist_session_id ON waitlist(session_id);
CREATE INDEX idx_waitlist_user_id    ON waitlist(user_id);

-- -------------------------------------------------------------------------
-- NOTIFICATION
-- session_id is nullable: some notifications are profile-level (e.g. plan expiry).
-- -------------------------------------------------------------------------
CREATE TABLE notification (
    id           BIGSERIAL   PRIMARY KEY,
    type         VARCHAR(20) NOT NULL
                     CHECK (type IN ('REMINDER', 'CONFIRMATION', 'CANCELLATION')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    sent         BOOLEAN     NOT NULL DEFAULT FALSE,
    sent_at      TIMESTAMPTZ,
    user_id      BIGINT      NOT NULL REFERENCES app_user(id)      ON DELETE RESTRICT,
    session_id   BIGINT               REFERENCES class_session(id) ON DELETE SET NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_sent         ON notification(sent);
CREATE INDEX idx_notification_scheduled_at ON notification(scheduled_at);

-- -------------------------------------------------------------------------
-- RATING  (one rating per user per session)
-- -------------------------------------------------------------------------
CREATE TABLE rating (
    id         BIGSERIAL   PRIMARY KEY,
    score      INT         NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment    TEXT,
    rated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id)      ON DELETE RESTRICT,
    session_id BIGINT      NOT NULL REFERENCES class_session(id) ON DELETE RESTRICT,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, session_id)
);

-- -------------------------------------------------------------------------
-- MEMBERSHIP_PLAN
-- -------------------------------------------------------------------------
CREATE TABLE membership_plan (
    id                 BIGSERIAL      PRIMARY KEY,
    name               VARCHAR(100)   NOT NULL,
    description        TEXT,
    price_monthly      NUMERIC(10, 2) NOT NULL,
    classes_per_month  INTEGER,                    -- NULL = unlimited
    allows_waitlist    BOOLEAN        NOT NULL DEFAULT TRUE,
    active             BOOLEAN        NOT NULL DEFAULT TRUE,
    duration_months    INTEGER        NOT NULL DEFAULT 1,
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- SUBSCRIPTION
-- -------------------------------------------------------------------------
CREATE TABLE subscription (
    id                      BIGSERIAL   PRIMARY KEY,
    user_id                 BIGINT      NOT NULL REFERENCES app_user(id)          ON DELETE RESTRICT,
    plan_id                 BIGINT      NOT NULL REFERENCES membership_plan(id)   ON DELETE RESTRICT,
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

-- -------------------------------------------------------------------------
-- REFRESH_TOKENS  (JWT rotation; raw tokens never stored — SHA-256 hash only)
-- -------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- -------------------------------------------------------------------------
-- PASSWORD_RESET_TOKENS
-- -------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id         BIGSERIAL   PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used       BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_prt_token_hash ON password_reset_tokens(token_hash);
