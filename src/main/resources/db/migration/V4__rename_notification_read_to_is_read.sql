-- 'read' is a reserved keyword in PostgreSQL and cannot be safely used as a bare
-- column identifier in all SQL contexts. Rename to 'is_read'.
-- Handles two possible DB states:
--   1. V2 was applied and 'read' column exists → rename it.
--   2. 'read' was never created (V2 failed silently) → add 'is_read' fresh.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'notification' AND column_name = 'read'
    ) THEN
        ALTER TABLE notification RENAME COLUMN "read" TO is_read;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
         WHERE table_name = 'notification' AND column_name = 'is_read'
    ) THEN
        ALTER TABLE notification ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

DROP INDEX IF EXISTS idx_notification_user_read;
CREATE INDEX IF NOT EXISTS idx_notification_user_is_read ON notification(user_id, is_read);
