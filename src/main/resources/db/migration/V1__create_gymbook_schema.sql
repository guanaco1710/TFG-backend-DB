-- V1__create_gymbook_schema.sql
-- Full GymBook schema. ON DELETE defaults to RESTRICT except where noted.
-- Enums stored as text + CHECK for easy evolution.
-- All timestamps use TIMESTAMPTZ (always UTC, unambiguous across DST).
-- 'app_user' instead of 'user' because user is a reserved word in PostgreSQL.

-- -------------------------------------------------------------------------
-- CLASS_TYPE  (template — e.g. "Spinning 45 min")
-- -------------------------------------------------------------------------
CREATE TABLE class_type (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    level       VARCHAR(20)  NOT NULL
                    CHECK (level IN ('BASIC', 'INTERMEDIATE', 'ADVANCED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- INSTRUCTOR
-- -------------------------------------------------------------------------
CREATE TABLE instructor (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    specialty   VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- APP_USER  (customer / instructor / admin)
-- PII: email, name, phone — apply data-retention policy before going live.
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
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- CLASS_SESSION  (concrete scheduled occurrence of a class type)
-- version column supports JPA optimistic locking (@Version) to prevent
-- double-booking races without a SELECT … FOR UPDATE on the whole row.
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
    instructor_id    BIGINT               REFERENCES instructor(id)  ON DELETE SET NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -------------------------------------------------------------------------
-- BOOKING
-- UNIQUE (user_id, session_id) prevents a user booking the same session twice.
-- -------------------------------------------------------------------------
CREATE TABLE booking (
    id         BIGSERIAL   PRIMARY KEY,
    booked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status     VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'
                   CHECK (status IN ('CONFIRMED', 'CANCELLED', 'ATTENDED', 'NO_SHOW')),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id)     ON DELETE RESTRICT,
    session_id BIGINT      NOT NULL REFERENCES class_session(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, session_id)
);

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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, session_id)
);

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
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, session_id)
);
