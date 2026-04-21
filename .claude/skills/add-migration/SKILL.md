---
name: add-migration
description: Create a new Flyway migration file with the correct next version number. Use when the user says "add a migration for X" or when another skill needs a migration scaffold. Figures out the next V<N> by scanning src/main/resources/db/migration.
---

# /add-migration

## Input

A snake_case description of the migration from `$ARGUMENTS` (e.g. `create_booking_table`, `add_index_on_session_start`). Ask if missing.

## Steps

1. **Ensure Flyway is configured.** Check `pom.xml` for `flyway-core` and `application.yaml` for Flyway settings. If missing, stop and tell the user to run `/setup-jpa` first.

2. **Find next version.** List `src/main/resources/db/migration/V*.sql`. The new version is `max + 1` (start at `V1` if none). Zero-pad is not required — Flyway compares numerically.

3. **Create the file** at `src/main/resources/db/migration/V<N>__<description>.sql` with a one-line SQL comment header describing the migration's purpose. Leave the body empty for the user (or caller) to fill in.

4. **Remind** the user:
   - Migrations are immutable once merged — if this one is wrong, create another to fix it.
   - Test migrations against Testcontainers Postgres, never H2.

## Output

The file path and version number.
