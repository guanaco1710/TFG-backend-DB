---
name: setup-jpa
description: One-time bootstrap of the persistence stack — adds spring-boot-starter-data-jpa, Flyway, a base auditable entity, datasource config, and a docker-compose Postgres for local dev. Use when the project still has no JPA wiring and the first entity is about to land.
---

# /setup-jpa

## When to run

Only once, when the project has no JPA setup yet. If `spring-boot-starter-data-jpa` is already on `pom.xml`, stop and tell the user it's already set up.

## Steps

1. **Add dependencies** to `pom.xml`:
   - `spring-boot-starter-data-jpa`
   - `spring-boot-starter-validation`
   - `flyway-core` and `flyway-database-postgresql`
   - (Testcontainers modules: `testcontainers`, `junit-jupiter`, `postgresql` — scope `test`)

2. **Configure `application.yaml`** with a datasource pointing at env vars (`${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`), JPA `ddl-auto: validate` (never `update` in prod), Flyway enabled, Hibernate naming strategy defaults. Add an `application-dev.yaml` with sane local defaults.

3. **Create a base entity** `common/BaseEntity.java` as a `@MappedSuperclass` with `@Id`/`@GeneratedValue`, `@Version`, and Spring Data auditing (`@CreatedDate`, `@LastModifiedDate` on `Instant` fields). Enable auditing with `@EnableJpaAuditing` in `config/JpaConfig.java`.

4. **Flyway scaffolding.** Create `src/main/resources/db/migration/` with a placeholder `.gitkeep`. Do not create `V1__init.sql` — let the first real entity drive it.

5. **Docker Compose.** Create `docker-compose.yml` at the project root with a Postgres 16 service exposing 5432, volume-mounted, matching the dev datasource creds. Delegate to the `devops-config` agent if the compose file needs anything beyond the basics.

6. **Verify.** `./mvnw clean compile`. Report the files added, new dependencies, and the command to start Postgres (`docker compose up -d`).

## Don'ts

- Don't commit real credentials. Use env vars with local defaults only in `application-dev.yaml`.
- Don't enable `ddl-auto: update` for any profile — Flyway owns schema changes.
