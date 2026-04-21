---
name: new-entity
description: Add a new JPA entity with its Flyway migration and repository. Use when the user asks to model a new domain object ("add a Waitlist entity", "model MembershipPlan"). Delegates entity design to jpa-modeler and wires up the migration file.
---

# /new-entity

## Input

Entity name and a short description of its fields/relationships from `$ARGUMENTS`. Ask if missing.

## Steps

1. **Sanity check domain fit.** Confirm the entity belongs to the domain model described in `CLAUDE.md`. If it's close to an existing concept (e.g. user wants `Reservation` but we already have `Booking`), flag and ask before duplicating.

2. **Design the entity.** Invoke the `jpa-modeler` agent with the requested fields and relationships. Expect back: the `@Entity` class, repository interface, and the DDL for a Flyway migration.

3. **Add the migration.** Run the `/add-migration` skill with a descriptive name (e.g. `create_waitlist_table`) to generate the next `V<N>__*.sql` file, then paste the DDL from step 2 into it.

4. **Verify compile.** `./mvnw compile` — fix any import or mapping issues before declaring done.

5. **Remind** the user that:
   - Tests for the repository go through the `spring-test-writer` agent (`@DataJpaTest` + Testcontainers).
   - Any feature exposing this entity should go through `/new-feature`, not manual controller wiring.

## Output

Files created, migration version number, next suggested step.
