-- V3__add_version_columns.sql
-- Add optimistic-locking version columns to all tables that did not have one in V1.
-- class_session already has version (added in V1).
-- DEFAULT 0 ensures existing rows start at version 0, matching Hibernate's expectation.

ALTER TABLE class_type  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE instructor  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE booking     ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE waitlist    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE rating      ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
