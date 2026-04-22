-- V2__add_indexes.sql
-- Postgres does NOT auto-index foreign keys.
-- Every FK used in joins or WHERE filters gets an explicit index here.

-- Schedule browsing: filter/sort by start time
CREATE INDEX idx_class_session_start_time  ON class_session(start_time);
CREATE INDEX idx_class_session_status      ON class_session(status);

-- Booking lookups by user and session
CREATE INDEX idx_booking_user_id           ON booking(user_id);
CREATE INDEX idx_booking_session_id        ON booking(session_id);
CREATE INDEX idx_booking_status            ON booking(status);

-- Waitlist lookups
CREATE INDEX idx_waitlist_session_id       ON waitlist(session_id);
CREATE INDEX idx_waitlist_user_id          ON waitlist(user_id);

-- Notification delivery: "find all unsent notifications due now"
CREATE INDEX idx_notification_sent         ON notification(sent);
CREATE INDEX idx_notification_scheduled_at ON notification(scheduled_at);

-- app_user.email is already covered by the UNIQUE constraint index;
-- explicit name for consistent naming conventions across the schema.
CREATE INDEX idx_app_user_email            ON app_user(email);
