-- Initialize database
-- This script sets up the basic schema structure
-- Flyway migrations will handle all schema version management

CREATE SCHEMA IF NOT EXISTS public;

-- Create flyway schema history table if needed
CREATE TABLE IF NOT EXISTS flyway_schema_history (
  installed_rank INT NOT NULL,
  version VARCHAR(50),
  description VARCHAR(255) NOT NULL,
  type VARCHAR(20) NOT NULL,
  script VARCHAR(1000) NOT NULL,
  checksum INT,
  installed_by VARCHAR(100) NOT NULL,
  installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  execution_time INT NOT NULL,
  success BOOLEAN NOT NULL,
  PRIMARY KEY (installed_rank)
);

GRANT ALL PRIVILEGES ON DATABASE tfg_db TO tfg_user;
GRANT ALL PRIVILEGES ON SCHEMA public TO tfg_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO tfg_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO tfg_user;
