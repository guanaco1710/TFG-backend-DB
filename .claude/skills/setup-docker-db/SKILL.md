---
name: setup-docker-db
description: Create or update docker-compose.yml with a Postgres 16 service and wire the dev datasource config in application-dev.yaml. Use when you want to run the database in Docker for local development.
---

# /setup-docker-db

Sets up a local Postgres container via Docker Compose and wires the Spring Boot dev datasource to point at it.

## When to run

When the user wants to host the database in Docker for local development. Safe to re-run — it updates existing files rather than overwriting them blindly.

## Steps

1. **Check for existing docker-compose.yml** at the project root.
   - If it exists, read it and update the `db` service in place rather than overwriting the whole file.
   - If it doesn't exist, create it from scratch.

2. **Write / update `docker-compose.yml`** with a Postgres 16 service:

   ```yaml
   services:
     db:
       image: postgres:16
       environment:
         POSTGRES_DB: tfg
         POSTGRES_USER: tfg
         POSTGRES_PASSWORD: tfg
       ports:
         - "5432:5432"
       volumes:
         - pgdata:/var/lib/postgresql/data
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U tfg -d tfg"]
         interval: 10s
         timeout: 5s
         retries: 5
   volumes:
     pgdata:
   ```

3. **Create or update `src/main/resources/application-dev.yaml`** with a datasource block:

   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/tfg
       username: tfg
       password: tfg
       driver-class-name: org.postgresql.Driver
   ```

   - Only set these in the `dev` profile file — never hardcode credentials in `application.yaml`.
   - If `application-dev.yaml` already has a datasource block, update it in place.

4. **Delegate** any non-trivial compose changes (e.g. adding an `app` service, network config, multi-container setup) to the `docker-manager` agent.

5. **Verify**: run `docker compose up -d db` and confirm the container becomes healthy, then `./mvnw compile` to make sure nothing broke on the Java side.

6. **Output** a summary:
   - Files created / modified
   - Command to start the DB: `docker compose up -d db`
   - Command to stop it: `docker compose down`
   - JDBC URL for a DB client: `jdbc:postgresql://localhost:5432/tfg` (user: `tfg`, pass: `tfg`)

## Don'ts

- Don't commit real passwords. The credentials above (`tfg`/`tfg`) are local-dev only and safe to include in `application-dev.yaml`.
- Don't run `docker compose down -v` — it destroys volume data.
- Don't touch `application.yaml` (prod-safe defaults) — only `application-dev.yaml`.
- Don't add JPA or Flyway config here — that belongs in `/setup-jpa`.
