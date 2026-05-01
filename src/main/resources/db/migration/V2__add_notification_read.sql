ALTER TABLE notification ADD COLUMN IF NOT EXISTS read BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_notification_user_read ON notification(user_id, read);
