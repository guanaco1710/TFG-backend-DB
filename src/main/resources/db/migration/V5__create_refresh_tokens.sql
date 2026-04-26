-- V5__create_refresh_tokens.sql
-- Refresh token store for JWT rotation.
-- Raw tokens are never stored; only a SHA-256 hex digest is persisted.
-- On theft detection (a replaced token is reused) the entire family is revoked.

CREATE TABLE refresh_tokens (
    id          BIGSERIAL    PRIMARY KEY,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 hex (64 chars)
    user_id     BIGINT       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
