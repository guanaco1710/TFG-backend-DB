-- Initialize database
-- This script sets up the basic schema structure
-- Flyway migrations will handle all schema version management

CREATE SCHEMA IF NOT EXISTS public;

-- NOTE: Do NOT pre-create flyway_schema_history here.
-- Flyway owns that table and creates it itself on first run.
-- A manually created version with the wrong schema causes startup failures.

GRANT ALL PRIVILEGES ON DATABASE "GymBook_DB" TO tfg_user;
GRANT ALL PRIVILEGES ON SCHEMA public TO tfg_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO tfg_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO tfg_user;
