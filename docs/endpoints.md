# API Endpoints

Base path: `/api/v1`

All protected endpoints require a valid JWT `Authorization: Bearer <token>` header.

Roles: `CUSTOMER`, `INSTRUCTOR`, `ADMIN`

---

## Auth

All `/auth/**` endpoints are public — no `Authorization` header required.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/auth/register` | Register a new account |
| `POST` | `/auth/login` | Obtain access + refresh tokens |
| `POST` | `/auth/refresh` | Rotate refresh token, get new token pair |
| `POST` | `/auth/logout` | Revoke a refresh token (logout this device) |
| `POST` | `/auth/forgot-password` | Request a password-reset token |
| `POST` | `/auth/reset-password` | Apply a new password using the reset token |

### Auth response shape

Login, register, and refresh all return the same envelope:

```json
{
  "tokens": {
    "accessToken": "string",
    "refreshToken": "string",
    "expiresInSeconds": 900
  },
  "user": {
    "id": 1,
    "name": "Alice Smith",
    "email": "alice@example.com",
    "role": "CUSTOMER"
  }
}
```

`role` is one of `CUSTOMER`, `INSTRUCTOR`, `ADMIN`.  
`expiresInSeconds` is the lifetime of the **access token** (default 900 s / 15 min).

---

### POST /auth/register

```json
// Request
{
  "name": "string",        // required
  "email": "string",       // required, must be a valid email
  "password": "string",    // required, min 8 characters
  "role": "CUSTOMER"       // optional, defaults to CUSTOMER
}

// Response 201 + Location: /api/v1/users/{id}
// Body: auth response envelope (see above)
```

The user is immediately logged in — no second login call needed.

---

### POST /auth/login

```json
// Request
{ "email": "string", "password": "string" }

// Response 200
// Body: auth response envelope (see above)
```

Returns 401 for both unknown email and wrong password (no user enumeration).

---

### POST /auth/refresh

```json
// Request
{ "refreshToken": "string" }

// Response 200
// Body: auth response envelope (see above) — includes refreshed user identity
```

The old refresh token is immediately revoked (rotation). Re-using a consumed token returns 401.

---

### POST /auth/logout

```json
// Request
{ "refreshToken": "string" }

// Response 204 No Content
```

Revokes the supplied refresh token. Idempotent — silently succeeds if the token is already revoked or unknown. The access token remains valid until it expires naturally; clients should discard it locally.

---

### POST /auth/forgot-password

```json
// Request
{ "email": "string" }

// Response 200
{
  "message": "If that email is registered, a reset link has been sent.",
  "resetToken": "string | null"
}
```

Always returns 200 regardless of whether the email exists (no enumeration). `resetToken` is non-null only for known emails — in production this would be delivered by email; the raw token is included in the response for development purposes only.

---

### POST /auth/reset-password

```json
// Request
{
  "token": "string",        // raw reset token from forgot-password response
  "newPassword": "string"   // min 8 characters
}

// Response 200
```

Returns 401 if the token is unknown, already used, or expired (15-minute window).

---

## Users

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/users/me` | Get own profile | Any authenticated |
| `PATCH` | `/users/me` | Update own name / phone / specialty | Any authenticated |
| `GET` | `/users` | List all users (paginated) | `ADMIN` |
| `GET` | `/users/{id}` | Get any user by ID | `ADMIN` |
| `PATCH` | `/users/{id}` | Update any user (name, phone, role, active) | `ADMIN` |
| `DELETE` | `/users/{id}` | Soft-delete a user (sets active=false) | `ADMIN` |

### UserResponse shape (all user endpoints return this)

```json
{
  "id": 1,
  "name": "Alice Smith",
  "email": "alice@example.com",
  "phone": "+34 911 000 001",
  "role": "CUSTOMER",
  "active": true,
  "createdAt": "2024-01-01T00:00:00Z",
  "specialty": null
}
```

> **Note for frontend:** field is `name` (single field), NOT `firstName`/`lastName`. `specialty` is non-null only for `INSTRUCTOR` role. `avatarUrl` does not exist — not implemented.

### GET /users — Query params
| Param | Type | Description |
|-------|------|-------------|
| `role` | `CUSTOMER\|INSTRUCTOR\|ADMIN` | Filter by role |
| `page` | int | Page number (0-based) |
| `size` | int | Page size (default 20) |

### GET /users — Response 200
```json
{
  "content": [
    {
      "id": 1,
      "name": "Alice Smith",
      "email": "alice@example.com",
      "phone": "+34 911 000 001",
      "role": "CUSTOMER",
      "active": true,
      "createdAt": "2024-01-01T00:00:00Z",
      "specialty": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "hasMore": true
}
```

### PATCH /users/me — Request
```json
{
  "name": "string",
  "phone": "string",
  "specialty": "string"
}
// All fields optional (partial update). Response 200 — updated UserResponse.
```

---

## Gyms

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/gyms` | List gyms | Any |
| `GET` | `/gyms/{id}` | Get gym by ID | Any |
| `POST` | `/gyms` | Create a gym | `ADMIN` |
| `PUT` | `/gyms/{id}` | Update a gym | `ADMIN` |
| `DELETE` | `/gyms/{id}` | Delete a gym | `ADMIN` |

### GET /gyms — Query params

| Param | Type | Description |
|-------|------|-------------|
| `name` | String | Filter by gym name (case-insensitive partial match) |
| `city` | String | Filter by city (exact, case-insensitive) |
| `active` | Boolean | Filter by active status |
| `q` | String | Full-text search across name and address |
| `page` | Int | Page index (0-based, default 0) |
| `size` | Int | Page size (default 20) |

### GET /gyms — Response 200
```json
{
  "content": [
    {
      "id": 1,
      "name": "GymBook Central",
      "address": "Calle Mayor 1",
      "city": "Madrid",
      "phone": "+34 91 000 0000",
      "openingHours": "Mon–Fri 07:00–22:00, Sat–Sun 09:00–20:00",
      "active": true,
      "createdAt": "2024-01-01T00:00:00Z",
      "updatedAt": "2024-01-01T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasMore": false
}
```

### POST /gyms — Request
```json
{
  "name": "string",
  "address": "string",
  "city": "string",
  "phone": "string",
  "openingHours": "string"
}
// Response 201 + Location: /api/v1/gyms/{id}
```

---

## Class Types

Templates for classes (e.g. "Spinning 45min").

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/class-types` | List class types | Any |
| `GET` | `/class-types/{id}` | Get class type by ID | Any |
| `POST` | `/class-types` | Create a class type | `ADMIN` |
| `PUT` | `/class-types/{id}` | Update a class type | `ADMIN` |
| `DELETE` | `/class-types/{id}` | Delete a class type | `ADMIN` |

### POST /class-types — Request
```json
{
  "name": "string",
  "description": "string",
  "durationMinutes": 45,
  "category": "string",
  "iconUrl": "string"
}
// Response 201 + Location: /api/v1/class-types/{id}
```

---

## Class Sessions

Concrete scheduled occurrences of a class type.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/class-sessions` | List sessions (paginated) | Any authenticated |
| `GET` | `/class-sessions/schedule` | Sessions in a datetime range (flat list) | Any authenticated |
| `GET` | `/class-sessions/{id}` | Get session detail | Any authenticated |
| `POST` | `/class-sessions` | Schedule a session | `ADMIN` |
| `PUT` | `/class-sessions/{id}` | Update a session | `ADMIN` |
| `POST` | `/class-sessions/{id}/cancel` | Cancel a session | `ADMIN` |

> **Known issue for frontend:** `startTime` serialises as `LocalDateTime` with **no timezone** (e.g. `"2024-06-01T09:00:00"`). Parse it as the gym's local time until a backend fix lands.

### GET /class-sessions — Query params
| Param | Type | Description |
|-------|------|-------------|
| `gymId` | Long | Filter by gym (use the user's subscribed gym to show relevant classes) |
| `classTypeId` | Long | Filter by class type |
| `page` | int | |
| `size` | int | |

Both params are optional and combinable. Omitting both returns all sessions across all gyms.

### GET /class-sessions — Response 200
```json
{
  "content": [
    {
      "id": 1,
      "classType": { "id": 1, "name": "Spinning 45min", "level": "INTERMEDIATE" },
      "gym": { "id": 1, "name": "GymBook Central", "address": "Calle Mayor 1", "city": "Madrid" },
      "instructor": { "id": 2, "name": "Jane Doe", "specialty": "Cycling" },
      "startTime": "2024-06-01T09:00:00",
      "durationMinutes": 45,
      "maxCapacity": 20,
      "room": "Studio A",
      "status": "SCHEDULED",
      "confirmedCount": 15,
      "availableSpots": 5
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3,
  "hasMore": true
}
```

`status` is one of `SCHEDULED`, `ACTIVE`, `CANCELLED`, `FINISHED`.

### GET /class-sessions/schedule — Query params
| Param | Type | Description |
|-------|------|-------------|
| `from` | ISO datetime | Start of range (required) |
| `to` | ISO datetime | End of range (required) |

Returns a flat `List` (no pagination). Same object shape as content items above.

### POST /class-sessions — Request
```json
{
  "classTypeId": 1,
  "gymId": 1,
  "instructorId": 2,
  "startTime": "2024-06-01T09:00:00",
  "durationMinutes": 45,
  "maxCapacity": 20,
  "room": "Studio A"
}
// Response 201 + Location: /api/v1/class-sessions/{id}
```

---

## Bookings

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/bookings` | Book a spot in a class session | `CUSTOMER` |
| `GET` | `/bookings/{id}` | Get booking by ID | Owner, `INSTRUCTOR` (own sessions), `ADMIN` |
| `PATCH` | `/bookings/{id}/cancel` | Cancel a booking | Owner, `ADMIN` |
| `GET` | `/bookings/me` | List own bookings (paginated) | `CUSTOMER` |
| `GET` | `/bookings` | List all bookings (paginated) | `ADMIN` |

### POST /bookings — Request
```json
{ "classSessionId": 1 }
```

### POST /bookings — Response 201
```json
{
  "id": 42,
  "classSession": {
    "id": 1,
    "classType": { "id": 1, "name": "Spinning 45min" },
    "startTime": "2024-06-01T09:00:00"
  },
  "status": "CONFIRMED",
  "waitlistPosition": null,
  "bookedAt": "2024-05-20T10:00:00Z"
}
// If session is full → status: "WAITLISTED", waitlistPosition: 3
```

### PATCH /bookings/{id}/cancel — Response 200
```json
{
  "id": 42,
  "status": "CANCELLED",
  "cancelledAt": "2024-05-21T08:00:00Z"
}
```

### GET /bookings/me — Query params
| Param | Type | Description |
|-------|------|-------------|
| `status` | `CONFIRMED\|WAITLISTED\|CANCELLED\|ATTENDED\|NO_SHOW` | Filter by status |
| `from` | ISO date | |
| `to` | ISO date | |
| `page` | int | |
| `size` | int | |

### GET /bookings — Query params (admin)
| Param | Type | Description |
|-------|------|-------------|
| `userId` | Long | Filter by user |
| `classSessionId` | Long | Filter by session |
| `status` | string | Filter by booking status |
| `page` | int | |
| `size` | int | |

---

## Membership Plans

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/membership-plans` | List available plans | Any |
| `GET` | `/membership-plans/{id}` | Get plan details | Any |
| `POST` | `/membership-plans` | Create a plan | `ADMIN` |
| `PUT` | `/membership-plans/{id}` | Update a plan | `ADMIN` |
| `DELETE` | `/membership-plans/{id}` | Deactivate a plan | `ADMIN` |

Plans are **global** — not tied to a specific gym. When subscribing, the chosen gym is provided in the POST /subscriptions body alongside the plan.

### GET /membership-plans — Query params

| Param | Type | Description |
|-------|------|-------------|
| `active` | Boolean | Filter by active status (omit to return all) |
| `page` | int | Page index (0-based, default 0) |
| `size` | int | Page size (default 20) |

### GET /membership-plans — Response 200
```json
{
  "content": [
    {
      "id": 1,
      "name": "Basic",
      "description": "Up to 12 classes per month",
      "priceMonthly": 29.99,
      "classesPerMonth": 12,
      "allowsWaitlist": false,
      "active": true
    },
    {
      "id": 2,
      "name": "Unlimited",
      "description": "Unlimited classes, waitlist priority",
      "priceMonthly": 49.99,
      "classesPerMonth": null,
      "allowsWaitlist": true,
      "active": true
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "hasMore": false
}
```

`classesPerMonth: null` means unlimited.

### POST /membership-plans — Request
```json
{
  "name": "string",
  "description": "string",
  "priceMonthly": 49.99,
  "classesPerMonth": 12,
  "allowsWaitlist": true,
  "active": true
}
// Response 201 + Location: /api/v1/membership-plans/{id}
```

---

## Subscriptions

A user can hold **one active subscription per gym**. Multiple gyms = multiple subscriptions.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/subscriptions/me` | List own subscriptions (all gyms) | Any authenticated |
| `POST` | `/subscriptions` | Subscribe to a plan at a gym | Any authenticated |
| `POST` | `/subscriptions/{id}/cancel` | Cancel subscription (end of period) | Owner or `ADMIN` |
| `POST` | `/subscriptions/{id}/upgrade` | Schedule plan upgrade for next billing cycle | Owner or `ADMIN` |
| `POST` | `/subscriptions/{id}/renew` | Admin renew/reactivate a subscription | `ADMIN` |
| `GET` | `/subscriptions` | List all subscriptions (paginated) | `ADMIN` |

### POST /subscriptions — Request
```json
{
  "membershipPlanId": 2,
  "gymId": 1
}
// Response 201 + Location: /api/v1/subscriptions/{id}
// 409 if user already has an ACTIVE subscription for that gym
```

### GET /subscriptions/me — Response 200

Always returns an **array** — all subscriptions for the authenticated user across all gyms.  
Returns `[]` when the user has no subscriptions. HTTP 200 always (no 404).

```json
[
  {
    "id": 7,
    "plan": { "id": 2, "name": "Premium Monthly", "priceMonthly": 49.99 },
    "gym": { "id": 1, "name": "GymBook Central", "address": "Calle Mayor 1", "city": "Madrid" },
    "status": "ACTIVE",
    "startDate": "2024-05-01",
    "renewalDate": "2024-06-01",
    "classesUsedThisMonth": 5,
    "classesRemainingThisMonth": 7,
    "pendingCancellation": false,
    "cancelledAt": null,
    "pendingPlan": null
  }
]
```

`status` is one of `ACTIVE`, `CANCELLED`, `EXPIRED`.  
`classesRemainingThisMonth` is `null` when the plan grants unlimited classes.  
`pendingPlan` is non-null when an upgrade has been scheduled for the next billing cycle.

### POST /subscriptions/{id}/cancel — Response

Soft-cancel: subscription stays `ACTIVE` until `renewalDate`, then the scheduler flips it to `CANCELLED`.

```
Response 204 No Content
409 SubscriptionCancellationPending — already scheduled for cancellation
409 SubscriptionNotActive — subscription is not ACTIVE
404 SubscriptionNotFound
403 if non-owner and not ADMIN
```

### POST /subscriptions/{id}/upgrade — Request

Schedules a plan change that takes effect at the **next billing cycle** (on renewal), not immediately.

```json
{ "newMembershipPlanId": 3 }
```

```
Response 200 — subscription object with pendingPlan populated
404 SubscriptionNotFound
409 SubscriptionNotActive — subscription is not ACTIVE
409 MembershipPlanInactive — new plan is inactive
403 if non-owner and not ADMIN
```

```json
// Response 200 — example
{
  "id": 7,
  "plan": { "id": 2, "name": "Basic", "priceMonthly": 29.99 },
  "gym": { "id": 1, "name": "GymBook Central", "address": "Calle Mayor 1", "city": "Madrid" },
  "status": "ACTIVE",
  "startDate": "2024-05-01",
  "renewalDate": "2024-06-01",
  "classesUsedThisMonth": 5,
  "classesRemainingThisMonth": 7,
  "pendingCancellation": false,
  "cancelledAt": null,
  "pendingPlan": { "id": 3, "name": "Premium", "priceMonthly": 49.99 }
}
```

When `renewalDate` is reached, the scheduler applies `pendingPlan` as the new `plan` and clears `pendingPlan`.

### POST /subscriptions/{id}/renew — Response (admin only)

Forces an immediate renewal: advances `renewalDate` by `plan.durationMonths`, resets `classesUsedThisMonth` to 0, and applies `pendingPlan` if set.

```
Response 200 — updated subscription object
404 SubscriptionNotFound
409 SubscriptionCancellationPending — already scheduled for cancellation
```

### GET /subscriptions — Query params (admin)
| Param | Type | Description |
|-------|------|-------------|
| `userId` | Long | Filter by user |
| `status` | `ACTIVE\|CANCELLED\|EXPIRED` | Filter by status |
| `page` | int | |
| `size` | int | |

### GET /subscriptions — Response 200 (admin)
```json
{
  "content": [
    {
      "id": 7,
      "plan": { "id": 2, "name": "Premium Monthly", "priceMonthly": 49.99 },
      "gym": { "id": 1, "name": "GymBook Central", "address": "Calle Mayor 1", "city": "Madrid" },
      "status": "ACTIVE",
      "startDate": "2024-05-01",
      "renewalDate": "2024-06-01",
      "classesUsedThisMonth": 5,
      "classesRemainingThisMonth": 7,
      "pendingCancellation": false,
      "cancelledAt": null,
      "pendingPlan": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 198,
  "totalPages": 10,
  "hasMore": true
}
```

### Subscription business rules

- One **ACTIVE** subscription per user per gym. Attempting a second → `409 SubscriptionAlreadyActive`.
- Cancel is **soft**: `cancelledAt` is set, status stays `ACTIVE` until `renewalDate`. `pendingCancellation: true` signals this to clients.
- Upgrade is **deferred**: `pendingPlan` is set; applied automatically on the next renewal.
- A cancelled subscription (pending or confirmed) cannot be upgraded or re-cancelled.

### SubscriptionResponse fields

| Field | Type | Notes |
|-------|------|-------|
| `id` | Long | |
| `plan` | `{id, name, priceMonthly}` | Current active plan |
| `gym` | `{id, name, address, city}` | |
| `status` | `ACTIVE\|CANCELLED\|EXPIRED` | |
| `startDate` | ISO date | |
| `renewalDate` | ISO date | Next billing / expiry date |
| `classesUsedThisMonth` | int | |
| `classesRemainingThisMonth` | int or null | `null` = unlimited plan |
| `pendingCancellation` | boolean | `true` when `cancelledAt` is set and status is still `ACTIVE` |
| `cancelledAt` | ISO instant or null | When the cancel was requested |
| `pendingPlan` | `{id, name, priceMonthly}` or null | Plan queued for next billing cycle |

---

## Statistics

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/stats/me` | Own activity statistics | Any authenticated |
| `GET` | `/stats/me/history` | Paginated attendance history | Any authenticated |

> **Note for frontend:** `GET /stats/users/{id}`, `GET /stats/sessions/{id}`, and `GET /stats/gym` are **not implemented**.

### GET /stats/me — Response 200

```json
{
  "totalBookings": 42,
  "totalAttended": 38,
  "totalNoShows": 2,
  "totalCancellations": 4,
  "attendanceRate": 0.95,
  "currentStreak": 5,
  "favoriteClassType": "Spinning 45min",
  "classesBookedThisMonth": 8,
  "classesRemainingThisMonth": 12
}
```

| Field | Type | Notes |
|-------|------|-------|
| `totalBookings` | long | All bookings ever |
| `totalAttended` | long | Status = ATTENDED |
| `totalNoShows` | long | Status = NO_SHOW |
| `totalCancellations` | long | Status = CANCELLED |
| `attendanceRate` | double | `attended / (attended + noShows)`, `0.0` when no data |
| `currentStreak` | int | Consecutive days with at least one attended class |
| `favoriteClassType` | string or null | Name of most-attended class type |
| `classesBookedThisMonth` | long | Bookings created in current calendar month (UTC) |
| `classesRemainingThisMonth` | int or null | From active subscription; `null` = no sub or unlimited plan |

### GET /stats/me/history — Query params
| Param | Type | Description |
|-------|------|-------------|
| `page` | int | |
| `size` | int | |

### GET /stats/me/history — Response 200
```json
{
  "content": [
    {
      "id": 1,
      "recordedAt": "2024-05-20T10:00:00Z",
      "status": "ATTENDED",
      "session": {
        "id": 10,
        "startTime": "2024-05-20T09:00:00",
        "durationMinutes": 45,
        "room": "Studio A",
        "classType": { "id": 1, "name": "Spinning 45min", "level": "INTERMEDIATE" }
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 38,
  "totalPages": 2,
  "hasMore": true
}
```

---

## Ratings

Customers rate a session after attending it. One rating per user per session.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/ratings` | Submit a rating for an attended session | `CUSTOMER` |
| `PUT` | `/ratings/{id}` | Update own rating | `CUSTOMER` |
| `DELETE` | `/ratings/{id}` | Delete a rating | Owner or `ADMIN` |
| `GET` | `/ratings/session/{sessionId}` | List all ratings for a session (paginated) | Any authenticated |
| `GET` | `/ratings/me` | List own ratings (paginated) | Any authenticated |

### POST /ratings — Request
```json
{
  "sessionId": 1,
  "score": 5,
  "comment": "Great class!"
}
// Response 201 + Location: /api/v1/ratings/{id}
```

### POST /ratings — Response 201
```json
{
  "id": 10,
  "score": 5,
  "comment": "Great class!",
  "ratedAt": "2026-05-01T09:00:00Z",
  "userId": 1,
  "sessionId": 1
}
```

### PUT /ratings/{id} — Request
```json
{ "score": 4, "comment": "Updated comment" }
```

### GET /ratings/session/{sessionId} — Query params
| Param | Type | Description |
|-------|------|-------------|
| `page` | int | Page number (0-based) |
| `size` | int | Page size |
| `sort` | string | e.g. `ratedAt,desc` |

---

## Notifications

Notifications are created automatically by the backend on booking confirmed/cancelled and session cancelled. The Flutter app polls to retrieve and display them.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/notifications/me` | List own notifications (paginated, filterable) | Any authenticated |
| `GET` | `/notifications/me/unread-count` | Count unread notifications | Any authenticated |
| `GET` | `/notifications/{id}` | Get a single notification | Owner or `ADMIN` |
| `POST` | `/notifications/{id}/read` | Mark notification as read (idempotent) | Owner or `ADMIN` |
| `POST` | `/notifications/me/read-all` | Mark all own notifications as read | Any authenticated |
| `DELETE` | `/notifications/{id}` | Delete a notification | Owner or `ADMIN` |
| `GET` | `/notifications/users/{userId}` | List all notifications for a user | `ADMIN` |
| `GET` | `/class-sessions/{sessionId}/notifications` | List all notifications for a session | `INSTRUCTOR`, `ADMIN` |

### GET /notifications/me — Query params
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `type` | `CONFIRMATION\|REMINDER\|CANCELLATION` | — | Filter by type |
| `unreadOnly` | boolean | `false` | Only unread notifications |
| `sentOnly` | boolean | `true` | Only dispatched notifications |
| `page` | int | 0 | |
| `size` | int | 20 | |

### GET /notifications/me — Response 200
```json
{
  "content": [
    {
      "id": 1,
      "type": "CONFIRMATION",
      "scheduledAt": "2026-05-01T09:00:00Z",
      "sent": true,
      "sentAt": "2026-05-01T09:00:05Z",
      "read": false,
      "userId": 1,
      "sessionId": 10
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

### POST /notifications/{id}/read — Response 200
```json
{ "updated": 1 }
// Returns 0 if already read (idempotent)
```

### POST /notifications/me/read-all — Response 200
```json
{ "updated": 3 }
```

---

## Payment Methods

Stores billing card metadata only — no real payment processing.

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/users/{userId}/payment-methods` | List payment methods for a user | Owner or `ADMIN` |
| `POST` | `/users/{userId}/payment-methods` | Add a payment method | Owner or `ADMIN` |
| `GET` | `/users/{userId}/payment-methods/{pmId}` | Get a single payment method | Owner or `ADMIN` |
| `PATCH` | `/users/{userId}/payment-methods/{pmId}` | Set as default card | Owner or `ADMIN` |
| `DELETE` | `/users/{userId}/payment-methods/{pmId}` | Delete a payment method | Owner or `ADMIN` |

### POST /users/{userId}/payment-methods — Request
```json
{
  "cardType": "VISA",
  "last4": "4242",
  "expiryMonth": 12,
  "expiryYear": 2028,
  "cardholderName": "Alice Smith"
}
// Response 201 + Location: /api/v1/users/{userId}/payment-methods/{id}
```

### POST /users/{userId}/payment-methods — Response 201
```json
{
  "id": 1,
  "cardType": "VISA",
  "last4": "4242",
  "expiryMonth": 12,
  "expiryYear": 2028,
  "cardholderName": "Alice Smith",
  "isDefault": true,
  "createdAt": "2026-05-01T10:00:00Z"
}
```

Card types: `VISA`, `MASTERCARD`, `AMEX`, `DISCOVER`

### PATCH /users/{userId}/payment-methods/{pmId} — Request
```json
{ "isDefault": true }
// isDefault: false → 422 Unprocessable Entity
```

### Business rules
- First card added is automatically set as default
- Deleting the default card auto-promotes the most recently added sibling
- Expired cards (past expiry month/year) → 409 Conflict
- Duplicate `cardType + last4` for the same user → 409 Conflict

---

## Error Response Shape

All errors follow a consistent envelope:

```json
{
  "timestamp": "2024-05-20T10:00:00Z",
  "status": 404,
  "error": "BookingNotFound",
  "message": "Booking with id 42 not found",
  "path": "/api/v1/bookings/42"
}
```

### Common HTTP Status Codes

| Code | Meaning |
|------|---------|
| `200 OK` | Successful read or update |
| `201 Created` | Resource created; `Location` header present |
| `204 No Content` | Successful operation with no response body |
| `400 Bad Request` | Validation failure |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | Authenticated but insufficient role |
| `404 Not Found` | Resource does not exist (also used for IDOR) |
| `409 Conflict` | Business rule violation (e.g. already booked, duplicate card) |
| `422 Unprocessable Entity` | Logically invalid request (e.g. booking a cancelled session) |

---

## Booking State Machine

```
                ┌──────────────────────────────┐
                │        POST /bookings         │
                └────────────┬─────────────────┘
                             │
               ┌─────────────▼─────────────┐
               │  spots available?         │
               └──────┬────────────────────┘
                      │ yes              │ no + waitlist enabled
            ┌─────────▼──────┐    ┌──────▼──────────┐
            │   CONFIRMED    │    │   WAITLISTED     │
            └──┬──────────┬──┘    └──────┬───────────┘
               │          │             │ spot opens (booking cancelled)
               │ cancel   │ attend      │
               │          │        ┌────▼──────────┐
               │          │        │   CONFIRMED   │
               │          │        └───────────────┘
   ┌───────────▼──┐  ┌────▼────┐
   │  CANCELLED   │  │ATTENDED │
   └──────────────┘  └─────────┘
                  also: NO_SHOW (set by instructor/admin)
```
