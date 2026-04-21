# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Domain

Backend for a **gym management mobile app**. End users are gym customers; the app lets them:

- Browse the class schedule (spinning, yoga, crossfit, etc.)
- Book / cancel spots in classes (with capacity limits and waitlists)
- View their own activity statistics (attendance, streaks, calories, favorite classes)
- Manage profile, membership plan, and payment info

Secondary personas: **instructors** (see their roster per class) and **gym staff/admins** (create classes, manage members, view occupancy reports).

The core domain objects are roughly:

- `User` — customer, instructor, or admin (role-based)
- `Gym` — a physical location (the app may eventually support multiple gyms)
- `ClassType` — a template (e.g. "Spinning 45min"): name, description, default duration
- `ClassSession` — a concrete scheduled occurrence of a `ClassType` at a time, room, with an instructor and capacity
- `Booking` — a `User`'s reservation in a `ClassSession`; states: `CONFIRMED`, `WAITLISTED`, `CANCELLED`, `ATTENDED`, `NO_SHOW`
- `MembershipPlan` / `Subscription` — what the user pays for and what it entitles them to
- `Attendance` — records who actually showed up (drives statistics)

Keep this model in mind when naming things and designing APIs. Don't invent exotic abstractions — stay close to the vocabulary above.

## Commands

```bash
# Build
./mvnw clean compile
./mvnw clean package

# Run (dev)
./mvnw spring-boot:run

# Test (all / class / method)
./mvnw test
./mvnw test -Dtest=ClassName
./mvnw test -Dtest=ClassName#methodName
```

Always use the Maven wrapper (`./mvnw`), never a system-installed `mvn`.

## Stack

- **Java 21**, **Spring Boot 4.0.5**, Spring Web MVC
- **PostgreSQL** (driver present; datasource config still needs to be added to `application.yaml`)
- **Spring Data JPA + Hibernate** expected for persistence (starter not yet on `pom.xml` — add when introducing the first entity)
- **Lombok** for boilerplate (`@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`)
- **Spring Boot DevTools** for hot reload in dev
- **Flyway** recommended for schema migrations once JPA is wired up (not yet added)
- **Spring Security + JWT** will be needed for auth (not yet added)

When a feature requires a dependency that isn't on `pom.xml` yet, add it — don't improvise a workaround.

## Architecture

Root package: `com.example.tfgbackend`.

Follow a standard layered Spring layout, organised **by feature** (not by technical layer) once things grow:

```
com.example.tfgbackend
├── TfgBackendApplication.java
├── config/                 # cross-cutting @Configuration beans (security, cors, jackson, ...)
├── common/                 # shared exceptions, error handlers, base entities, utils
├── booking/
│   ├── BookingController.java
│   ├── BookingService.java
│   ├── BookingRepository.java
│   ├── Booking.java        # JPA entity
│   └── dto/                # request/response records
├── classsession/
├── user/
├── stats/
└── ...
```

Rules of thumb:

- **Controllers** are thin: validate input, call the service, map to DTO, return `ResponseEntity`. No business logic.
- **Services** hold business logic and are transactional (`@Transactional` on write methods, `@Transactional(readOnly = true)` at class level when appropriate).
- **Repositories** extend `JpaRepository<Entity, Long>`. Use derived queries for simple cases, `@Query` (JPQL) when they get awkward. Avoid native SQL unless there's a concrete reason.
- **Entities never leave the service layer.** Controllers consume and return **DTOs** (Java `record`s are preferred for immutable DTOs). Map with a dedicated mapper or explicit constructor — no auto-magical exposure of JPA entities over the wire.
- **IDs** are `Long` with `@GeneratedValue(strategy = GenerationType.IDENTITY)` unless there's a reason to choose otherwise (e.g. UUIDs for public-facing identifiers).
- **Timestamps**: use `Instant` for audit fields (`createdAt`, `updatedAt`), `LocalDateTime`/`LocalDate`/`LocalTime` only when the value is genuinely wall-clock (e.g. a class's start time in the gym's local time). Prefer a base `@MappedSuperclass` with `@CreatedDate`/`@LastModifiedDate` (Spring Data auditing).
- **Validation**: Jakarta Bean Validation (`@NotNull`, `@Email`, `@Size`, ...) on DTOs, validated with `@Valid` in controllers. Translate violations to a consistent error response via `@RestControllerAdvice`.
- **Exceptions**: throw domain-specific exceptions (e.g. `ClassFullException`, `BookingNotFoundException`) from services; map them to HTTP statuses in a single `@RestControllerAdvice`. Don't leak `RuntimeException` / stack traces to clients.
- **Enums** for closed sets (`BookingStatus`, `UserRole`, `MembershipType`). Persist with `@Enumerated(EnumType.STRING)` — never `ORDINAL`.
- **Concurrency**: booking/cancellation must be safe under load. Use optimistic locking (`@Version`) on `ClassSession` or a transactional check-and-insert when counting seats. Document the chosen strategy in the service.

## REST conventions

- Base path: `/api/v1/...`
- Plural, lowercase nouns: `/api/v1/class-sessions`, `/api/v1/bookings`
- Standard verbs: `GET`, `POST`, `PUT` (or `PATCH`) for partial, `DELETE`
- Return `201 Created` with a `Location` header for POSTs that create resources
- Paginate list endpoints with Spring's `Pageable` (returning `Page<T>` serialised as the default JSON shape is fine for internal tools; for the mobile client prefer a leaner `{ content, page, size, total }` DTO)
- Error body shape (suggested):
  ```json
  { "timestamp": "...", "status": 404, "error": "BookingNotFound", "message": "...", "path": "/api/v1/bookings/42" }
  ```

## Testing

- **Unit tests** for services: Mockito for repositories, no Spring context. Fast.
- **Slice tests**: `@WebMvcTest` for controllers, `@DataJpaTest` for repositories.
- **Integration tests**: `@SpringBootTest` with **Testcontainers** (PostgreSQL) — never use H2 as a stand-in for Postgres because SQL dialects diverge.
- Name tests `MethodName_Condition_ExpectedResult` or use `@DisplayName` with a sentence.
- Every service method with branching logic deserves at least a happy-path and one failure-path test.

## Configuration

- `src/main/resources/application.yaml` for defaults.
- Per-env overrides via `application-dev.yaml`, `application-prod.yaml`, activated with `SPRING_PROFILES_ACTIVE`.
- **Never commit secrets**. Use env vars (`${DB_PASSWORD}`) or an external secret store.

## Current state

Scaffolded project with only `TfgBackendApplication` and an almost-empty `application.yaml`. No controllers, services, repositories, or entities yet. Datasource, JPA starter, Flyway, and Security all still need to be wired up — do this as the first real feature lands rather than as a separate "setup" commit with no functionality.

## Working with Claude in this repo

Specialised agents live in `.claude/agents/`. Pick the one whose scope matches the task; agents intentionally hand off to each other rather than overlap.

**Code (implementation)**
- **spring-feature** — implements a feature end-to-end across controller → service → repository → entity, following the conventions above.
- **jpa-modeler** — designs a single JPA entity / relationship (annotations, cascading, fetch types, indexes, matching Flyway migration).
- **spring-test-writer** — writes unit / slice (`@WebMvcTest`, `@DataJpaTest`) / Testcontainers-backed integration tests.
- **auth-specialist** — designs and implements auth: Spring Security config, JWT + refresh rotation, password hashing, role-based access, OAuth2 social login, password reset.

**Design (no implementation)**
- **api-designer** — proposes REST contracts (endpoints, DTOs, status codes, pagination, error envelope) before `spring-feature` implements them.
- **db-architect** — schema strategy for a subsystem, index/query planning, zero-downtime migration patterns, query-performance diagnosis. (Use `jpa-modeler` for a single entity.)

**Review**
- **security-auditor** — defensive security review of a diff or scope: OWASP, Spring pitfalls, secret handling, IDOR, input validation, rate limiting. Produces a prioritised findings list.

**Workflow**
- **github-manager** — branches, commits, PRs, issues, CI status, releases via `gh` CLI.
- **devops-config** — Spring profiles, `application.yaml` hierarchy, env vars, Dockerfile, docker-compose for local Postgres, GitHub Actions CI, observability (Actuator, structured logging, metrics).

Invoke with the `Agent` tool when a task matches. Also useful: built-in skills `/simplify`, `/review`, `/security-review`, `/fewer-permission-prompts`.
