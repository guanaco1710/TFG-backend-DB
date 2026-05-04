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
| `GET` | `/users` | List all users (paginated) | `ADMIN` |
| `GET` | `/users/{id}` | Get any user by ID | `ADMIN` |
| `POST` | `/users` | Create a user with any role (e.g. instructor) | `ADMIN` |
| `PUT` | `/users/{id}` | Update any user | `ADMIN` |
| `DELETE` | `/users/{id}` | Soft-delete a user | `ADMIN` |
| `GET` | `/users/me` | Get own profile | Any |
| `PUT` | `/users/me` | Update own profile | Any |
| `PATCH` | `/users/me/password` | Change own password | Any |

### GET /users — Query params
| Param | Type | Description |
|-------|------|-------------|
| `role` | `CUSTOMER\|INSTRUCTOR\|ADMIN` | Filter by role |
| `page` | int | Page number (0-based) |
| `size` | int | Page size (default 20) |
| `sort` | string | e.g. `lastName,asc` |

### GET /users — Response 200
```json
{
  "content": [
    {
      "id": 1,
      "email": "string",
      "firstName": "string",
      "lastName": "string",
      "role": "CUSTOMER",
      "active": true,
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5,
  "hasMore": true
}
```

### POST /users — Request
```json
{
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "password": "string",
  "role": "INSTRUCTOR"
}
// Response 201 + Location: /api/v1/users/{id}
```

### PUT /users/me — Request
```json
{
  "firstName": "string",
  "lastName": "string",
  "phone": "string",
  "avatarUrl": "string"
}
```

### PATCH /users/me/password — Request
```json
{ "currentPassword": "string", "newPassword": "string" }
// Response 204
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
| `GET` | `/class-sessions` | List sessions (schedule) | Any |
| `GET` | `/class-sessions/{id}` | Get session detail | Any |
| `POST` | `/class-sessions` | Schedule a session | `ADMIN` |
| `PUT` | `/class-sessions/{id}` | Update a session | `ADMIN` |
| `DELETE` | `/class-sessions/{id}` | Cancel a session | `ADMIN` |
| `GET` | `/class-sessions/{id}/bookings` | List all bookings for session | `INSTRUCTOR`, `ADMIN` |
| `POST` | `/class-sessions/{id}/attendance` | Mark attendance for session | `INSTRUCTOR`, `ADMIN` |

### GET /class-sessions — Query params
| Param | Type | Description |
|-------|------|-------------|
| `gymId` | Long | Filter by gym |
| `classTypeId` | Long | Filter by class type |
| `instructorId` | Long | Filter by instructor |
| `from` | ISO date | Start of date range (inclusive) |
| `to` | ISO date | End of date range (inclusive) |
| `status` | `SCHEDULED\|CANCELLED\|COMPLETED` | Filter by status |
| `availableOnly` | boolean | Only sessions with open spots |
| `page` | int | |
| `size` | int | |

### GET /class-sessions — Response 200
```json
{
  "content": [
    {
      "id": 1,
      "classType": { "id": 1, "name": "Spinning 45min" },
      "gym": { "id": 1, "name": "Main Gym" },
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
  "totalPages": 3
}
```

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

### POST /class-sessions/{id}/attendance — Request
```json
{
  "attendances": [
    { "userId": 10, "status": "ATTENDED" },
    { "userId": 11, "status": "NO_SHOW" }
  ]
}
// Response 204
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

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/subscriptions/me` | Get own current subscription | `CUSTOMER` |
| `POST` | `/subscriptions` | Subscribe to a membership plan | `CUSTOMER` |
| `POST` | `/subscriptions/me/cancel` | Cancel own subscription (end of period) | `CUSTOMER` |
| `GET` | `/subscriptions` | List all subscriptions (paginated) | `ADMIN` |
| `GET` | `/subscriptions/{id}` | Get subscription by ID | `ADMIN` |
| `POST` | `/subscriptions/{id}/cancel` | Admin cancel a subscription | `ADMIN` |
| `POST` | `/subscriptions/{id}/renew` | Admin renew/reactivate a subscription | `ADMIN` |

### POST /subscriptions — Request
```json
{
  "membershipPlanId": 2,
  "gymId": 1
}
// Response 201 + Location: /api/v1/subscriptions/{id}
```

### GET /subscriptions/me — Response 200

Always returns **one object** — the single active subscription for the authenticated user.  
Returns 404 if the user has no active subscription.

```json
{
  "id": 7,
  "plan": { "id": 2, "name": "Premium Monthly", "priceMonthly": 49.99 },
  "gym": { "id": 1, "name": "GymBook Central", "address": "Calle Mayor 1", "city": "Madrid" },
  "status": "ACTIVE",
  "startDate": "2024-05-01",
  "renewalDate": "2024-06-01",
  "classesUsedThisMonth": 5,
  "classesRemainingThisMonth": 7
}
```

`status` is one of `ACTIVE`, `CANCELLED`, `EXPIRED`.  
`classesRemainingThisMonth` is `null` when the plan grants unlimited classes.

### GET /subscriptions — Query params (admin)
| Param | Type | Description |
|-------|------|-------------|
| `userId` | Long | Filter by user |
| `planId` | Long | Filter by plan |
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
      "classesRemainingThisMonth": 7
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 198,
  "totalPages": 10,
  "hasMore": true
}
```

---

## Statistics

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/stats/me` | Own activity statistics | `CUSTOMER` |
| `GET` | `/stats/users/{id}` | Statistics for any user | `ADMIN` |
| `GET` | `/stats/sessions/{id}` | Occupancy stats for a session | `INSTRUCTOR`, `ADMIN` |
| `GET` | `/stats/gym` | Overall gym-level statistics | `ADMIN` |

### GET /stats/me — Query params
| Param | Type | Description |
|-------|------|-------------|
| `from` | ISO date | Start of period |
| `to` | ISO date | End of period |

### GET /stats/me — Response 200
```json
{
  "period": { "from": "2024-01-01", "to": "2024-05-31" },
  "totalClassesAttended": 38,
  "currentStreak": 5,
  "longestStreak": 14,
  "noShowCount": 2,
  "cancellationCount": 4,
  "favoriteClassType": { "id": 1, "name": "Spinning 45min", "attendanceCount": 20 },
  "attendanceByMonth": [
    { "month": "2024-01", "attended": 8 },
    { "month": "2024-02", "attended": 7 }
  ],
  "attendanceByClassType": [
    { "classTypeId": 1, "name": "Spinning 45min", "count": 20 }
  ]
}
```

### GET /stats/gym — Response 200
```json
{
  "period": { "from": "2024-01-01", "to": "2024-05-31" },
  "totalMembers": 250,
  "activeSubscriptions": 198,
  "totalClassesScheduled": 420,
  "averageOccupancyRate": 0.73,
  "mostPopularClassType": { "id": 1, "name": "Spinning 45min" },
  "peakHour": "09:00",
  "peakDayOfWeek": "TUESDAY"
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
