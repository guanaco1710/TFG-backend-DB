---
name: design-endpoint
description: Produce a REST contract (path, verb, DTOs, status codes, errors) for a proposed endpoint without implementing it. Use when the user wants to think through the API shape before coding, or when reviewing an API design. Delegates to the api-designer agent.
---

# /design-endpoint

## Input

A description of the endpoint from `$ARGUMENTS` (e.g. "cancel a booking", "list classes for a given week"). Ask if missing.

## Steps

1. **Delegate to `api-designer`** with the description and a reminder to follow CLAUDE.md's REST conventions:
   - Base path `/api/v1/...`
   - Plural lowercase nouns, kebab-case (`/class-sessions`)
   - `POST` returns `201` + `Location`; list endpoints paginate with `{ content, page, size, total }`.
   - Error envelope: `{ timestamp, status, error, message, path }`.

2. **Present the contract** to the user as a short spec — do NOT implement it. Include:
   - HTTP method + path
   - Request DTO (field names + types + validation constraints)
   - Response DTO
   - Success status + headers
   - Error cases mapped to status codes + domain exceptions
   - Auth requirements (role needed)

3. **Ask** whether to proceed to `/new-feature` for implementation.

## Don'ts

- Don't write controller or service code in this skill — design only.
