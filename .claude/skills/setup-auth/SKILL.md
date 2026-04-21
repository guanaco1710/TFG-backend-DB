---
name: setup-auth
description: Bootstrap Spring Security + JWT authentication for the gym backend. Use when the project needs login/register/refresh endpoints and role-based access (CUSTOMER, INSTRUCTOR, ADMIN). Delegates design and implementation to the auth-specialist agent.
---

# /setup-auth

## When to run

When there's no `SecurityConfig` yet and the project is about to expose endpoints that require authentication.

## Steps

1. **Precondition check.** Confirm JPA is set up (a `User` entity or at least the persistence stack). If not, stop and suggest `/setup-jpa` first.

2. **Delegate to `auth-specialist`** with this scope:
   - Password-based login (`POST /api/v1/auth/login`) returning access + refresh tokens.
   - Registration (`POST /api/v1/auth/register`) for `CUSTOMER` role by default.
   - Refresh rotation (`POST /api/v1/auth/refresh`).
   - Logout / token revocation strategy (document the choice — stateless blacklist vs. short-lived access tokens only).
   - `SecurityFilterChain` with JWT filter, BCrypt password encoder, method-level `@PreAuthorize` enabled.
   - Roles: `CUSTOMER`, `INSTRUCTOR`, `ADMIN` as a `UserRole` enum persisted `STRING`.
   - Error responses consistent with the project's `@RestControllerAdvice` error shape.

3. **Env vars for secrets.** JWT signing key via `${JWT_SECRET}`, token TTLs configurable in `application.yaml`. Never hard-code secrets.

4. **Tests.** Ask `spring-test-writer` to cover: login happy path, wrong password, expired token, role-gated endpoint (403 vs 401), refresh rotation.

5. **Security review.** Run the built-in `/security-review` on the diff before declaring done.

## Output

Files created, endpoints exposed, env vars required, and the command the user should run to generate a dev JWT secret.
