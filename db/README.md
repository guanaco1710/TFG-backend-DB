# Database Setup

PostgreSQL 16 database setup for the TFG Backend.

## Files

- **Dockerfile** — PostgreSQL 16 Alpine image; copies `init-scripts/` into the container.
- **init-scripts/01-init.sql** — Runs once on fresh volume creation: creates the `public` schema and grants privileges. Flyway takes over from here.
- **reference-schema.sql** — Human-readable reference of the full schema (kept in sync with Flyway migrations for documentation purposes; NOT executed at startup).

## Schema ownership

**Flyway is the single source of truth for the schema.**
`init-scripts/01-init.sql` only does bootstrap work (grants). All tables, indexes, and columns are created/modified by the Flyway migration files in `src/main/resources/db/migration/`.

## Usage

### Start the database container

From the project root:

```bash
docker-compose up -d
```

### Run the application (applies Flyway migrations automatically)

```bash
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run
```

Flyway connects to `localhost:5432/GymBook_DB` (using `tfg_user` / `tfg_password` by default) and applies any pending migrations on startup.

### View logs / stop / clean up

```bash
docker-compose logs -f postgres
docker-compose down
docker-compose down -v   # also removes the data volume
```

### Connection details (defaults)

| Property | Value |
|---|---|
| Host | localhost |
| Port | 5432 |
| Database | GymBook_DB |
| Username | tfg_user |
| Password | tfg_password |

Override any of these via environment variables (`POSTGRES_DB`, `DB_USERNAME`, `DB_PASSWORD`).

## Flyway migrations

Migration scripts live in `src/main/resources/db/migration/` and follow the naming convention `V<number>__<description>.sql`.

Current migrations:

| Version | Description |
|---|---|
| V1 | Full initial schema |
| V2 | Add `read` column + index to `notification` table |
