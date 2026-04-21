---
name: new-feature
description: Scaffold a new backend feature end-to-end (API contract → implementation → tests). Use when the user asks for a new domain capability like "add booking cancellation" or "expose user stats endpoint". Orchestrates api-designer, spring-feature, and spring-test-writer agents in sequence.
---

# /new-feature

End-to-end workflow for adding a feature to the gym backend.

## Input

One-line feature description from `$ARGUMENTS` (e.g. "book a class", "list upcoming sessions for a user"). If empty, ask the user what feature to build.

## Steps

1. **Confirm scope.** Restate the feature in one sentence and list the endpoints you expect to create. Ask for confirmation before proceeding if the request is ambiguous or touches domain areas not in `CLAUDE.md`.

2. **Design the API contract.** Invoke the `api-designer` agent with the feature description. Expect back: endpoint list, DTO shapes, status codes, error cases.

3. **Check dependencies.** Read `pom.xml`. If JPA / validation / security starters are missing and the feature needs them, stop and suggest running `/setup-jpa` or `/setup-auth` first.

4. **Implement.** Invoke the `spring-feature` agent, passing the contract from step 2. It will create the controller, service, repository, entity (if new), DTOs, and exception mapping under `com.example.tfgbackend.<feature>`.

5. **If a new entity was introduced**, invoke `jpa-modeler` to validate the mapping and produce a Flyway migration (or remind the user to run `/add-migration`).

6. **Tests.** Invoke the `spring-test-writer` agent to cover the service (unit), controller (`@WebMvcTest`), and repository (`@DataJpaTest`) plus at least one Testcontainers integration test for the happy path.

7. **Verify.** Run `./mvnw test` and report pass/fail. If compilation fails, fix and retry; if tests fail, surface the failure to the user before declaring done.

## Output

A short summary: endpoints added, files created/modified, migrations added, test results.

## Don'ts

- Don't skip the API design step even for "small" features — it's cheap and prevents rework.
- Don't invent domain concepts outside the model in `CLAUDE.md`. Ask instead.
