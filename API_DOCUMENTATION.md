# StreamHub Platform — API Documentation

Base URL (local): `http://localhost:8080/api/v1`

Every response is wrapped in a standard envelope:

```json
{
  "success": true,
  "message": "optional human-readable message",
  "data": { "...": "..." },
  "timestamp": "2026-08-15T10:32:04.123"
}
```

Errors use the same shape but with `success: false` and no `data` — see
[Error format](#error-format) at the bottom.

Auth: send `Authorization: Bearer <token>` for any endpoint marked
**Auth: Required**. Endpoints marked **Auth: Optional** behave differently
depending on whether a valid token is present. Endpoints marked
**Auth: Public** never require a token; **Auth: ADMIN** / **Auth:
ADMIN/ANALYTIC** require that role.

---

## Table of contents

1. [Authentication](#1-authentication)
2. [Users](#2-users)
3. [Categories](#3-categories)
4. [Videos](#4-videos)
5. [Playback](#5-playback)
6. [Playlists](#6-playlists)
7. [Watch History](#7-watch-history)
8. [Likes](#8-likes)
9. [Subscriptions](#9-subscriptions)
10. [Analytics](#10-analytics)
11. [Pagination format](#11-pagination-format)
12. [Error format](#12-error-format)

---

## 1. Authentication

### `POST /auth/register`
**Auth: Public**

Creates a new `NORMAL_USER` account and returns a JWT.

Request body:
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "supersecret1"
}
```

Response `201 Created`:
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "user": {
      "uid": "8b1f9e2a-6c3d-4e11-9a2f-1234567890ab",
      "username": "alice",
      "email": "alice@example.com",
      "roleType": "NORMAL_USER",
      "subscribed": false,
      "subscriptionTier": null,
      "subscriptionExpiry": null,
      "createdAt": "2026-08-15T10:00:00"
    }
  },
  "timestamp": "2026-08-15T10:00:00.512"
}
```

### `POST /auth/login`
**Auth: Public**

Request body:
```json
{ "email": "alice@example.com", "password": "supersecret1" }
```

Response `200 OK`: same shape as `register`, with message `"Login successful"`.

---

## 2. Users

### `GET /users/me`
**Auth: Required**

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "uid": "8b1f9e2a-6c3d-4e11-9a2f-1234567890ab",
    "username": "alice",
    "email": "alice@example.com",
    "roleType": "NORMAL_USER",
    "subscribed": false,
    "subscriptionTier": null,
    "subscriptionExpiry": null,
    "createdAt": "2026-08-15T10:00:00"
  },
  "timestamp": "2026-08-15T10:05:00.000"
}
```

---

## 3. Categories

### `GET /categories`
**Auth: Public** (Redis-cached, `@Cacheable("categories")`)

Response `200 OK`:
```json
{
  "success": true,
  "data": [
    { "id": "2f1a...-cat1", "name": "Music" },
    { "id": "2f1a...-cat2", "name": "Education" },
    { "id": "2f1a...-cat3", "name": "Entertainment" }
  ],
  "timestamp": "2026-08-15T10:06:00.000"
}
```

### `POST /categories`
**Auth: ADMIN**

Request body: `{ "name": "Cooking" }`
Response `201 Created`: `{ "success": true, "data": { "id": "...", "name": "Cooking" } }`

### `PUT /categories/{id}`
**Auth: ADMIN** — same body/response shape as create.

### `DELETE /categories/{id}`
**Auth: ADMIN** (soft delete)
Response: `{ "success": true, "message": "Category deleted" }`

---

## 4. Videos

### `GET /videos`
**Auth: Public**

Query params: `categoryId` (optional, UUID), `page`, `limit`, or `cursor`
(see [Pagination format](#11-pagination-format)).

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "b7e0...-vid1",
        "title": "Intro to Spring Boot",
        "thumbnailUrl": "/media/intro-to-spring-boot/intro-to-spring-boot_thumb.jpg",
        "videoUrl": "/media/intro-to-spring-boot/intro-to-spring-boot.m3u8",
        "views": 1204,
        "likes": 88,
        "categoryName": "Education",
        "createdAt": "2026-07-01T09:00:00"
      }
    ],
    "page": 0,
    "limit": 20,
    "totalElements": 57,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false,
    "nextCursor": "MDoyMA.MTox.abcDEF123signature"
  },
  "timestamp": "2026-08-15T10:07:00.000"
}
```

### `GET /videos/{id}`
**Auth: Public** (Redis-cached 15 min — repeated hits log `source=REDIS_CACHE`)

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "b7e0...-vid1",
    "title": "Intro to Spring Boot",
    "description": "A beginner-friendly walkthrough of Spring Boot fundamentals.",
    "videoUrl": "/media/intro-to-spring-boot/intro-to-spring-boot.m3u8",
    "thumbnailUrl": "/media/intro-to-spring-boot/intro-to-spring-boot_thumb.jpg",
    "durationSeconds": 734,
    "views": 1204,
    "likes": 88,
    "categoryId": "2f1a...-cat2",
    "categoryName": "Education",
    "createdAt": "2026-07-01T09:00:00"
  },
  "timestamp": "2026-08-15T10:07:05.000"
}
```

### `PATCH /videos/{id}/views`
**Auth: Optional** — guests increment the raw counter; if a valid
`Authorization` header is present, a watch-history row is also written.

Response `200 OK`: same shape as `GET /videos/{id}` with `views` incremented.

### `GET /videos/{id}/check-limit`
**Auth: Optional**

Query params: `sessionId` (optional, for guests). Does **not** increment
the counter — read-only.

Response `200 OK`:
```json
{
  "success": true,
  "data": { "locked": false, "remaining": 1, "unlimited": false },
  "timestamp": "2026-08-15T10:08:00.000"
}
```

For a subscribed user: `{ "locked": false, "remaining": -1, "unlimited": true }`

### `POST /videos/{id}/increment-watch`
**Auth: Optional** — same params/response shape as `check-limit`, but
increments the counter. Kept separate from `/playback` for frontend-side
tracking use cases.

### `PATCH /videos/{id}/category`
**Auth: ADMIN** — Query param `categoryId`. Response: video detail (as above).

### `DELETE /videos/{id}`
**Auth: ADMIN** (soft delete) — `{ "success": true, "message": "Video deleted" }`

---

## 5. Playback

### `GET /playback/{videoId}`
**Auth: Optional.** Optional header `x-session-id` (guest session tracking).
IP is read from `x-forwarded-for` / the socket automatically.

**Unlocked response** (subscriber, or free plays remaining) — `200 OK`:
```json
{
  "success": true,
  "data": {
    "manifestUrl": "/media/intro-to-spring-boot/intro-to-spring-boot.m3u8",
    "locked": false,
    "freeRemaining": 1
  },
  "timestamp": "2026-08-15T10:09:00.000"
}
```
(`freeRemaining` is omitted entirely for subscribed users, since the manifest
is unconditionally unlocked for them.)

**Locked response** (limit exceeded) — `200 OK`:
```json
{
  "success": true,
  "data": {
    "locked": true,
    "reason": "Account required - create an account to continue watching",
    "freeRemaining": 0
  },
  "timestamp": "2026-08-15T10:10:00.000"
}
```
(For an authenticated-but-unsubscribed user, `reason` becomes
`"Payment required - subscribe to continue watching"`.)

---

## 6. Playlists

All endpoints **Auth: Required**.

### `GET /playlists`
Response `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "c1a2...-pl1",
      "title": "My Favorites",
      "createdAt": "2026-08-10T12:00:00",
      "items": [
        {
          "id": "d3b4...-item1",
          "position": 0,
          "video": {
            "id": "b7e0...-vid1",
            "title": "Intro to Spring Boot",
            "thumbnailUrl": "/media/.../thumb.jpg",
            "videoUrl": "/media/.../manifest.m3u8",
            "views": 1204,
            "likes": 88,
            "categoryName": "Education",
            "createdAt": "2026-07-01T09:00:00"
          }
        }
      ]
    }
  ]
}
```

### `POST /playlists`
Body: `{ "title": "My Favorites" }` → `201 Created`, single playlist object as above (empty `items`).

### `POST /playlists/{playlistId}/add/{videoId}`
No body. Returns the updated playlist (as above).

### `DELETE /playlists/{playlistId}/remove/{videoId}`
Returns the updated playlist.

### `PATCH /playlists/{playlistId}/move/{videoId}/{newPosition}`
Returns the updated playlist with items re-indexed.

### `DELETE /playlists/{playlistId}`
`{ "success": true, "message": "Playlist deleted" }`

---

## 7. Watch History

All endpoints **Auth: Required**.

### `POST /history/{videoId}`
Upserts a history row (refreshes `watchedAt` if it already exists).

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "e5f6...-hist1",
    "video": {
      "id": "b7e0...-vid1", "title": "Intro to Spring Boot",
      "thumbnailUrl": "...", "videoUrl": "...", "views": 1205, "likes": 88,
      "categoryName": "Education", "createdAt": "2026-07-01T09:00:00"
    },
    "watchedAt": "2026-08-15T10:12:00"
  }
}
```

### `GET /history`
Paginated, most recent first. Response shape matches
[Pagination format](#11-pagination-format), with `content` items shaped like
the object above (minus the top-level wrapper).

---

## 8. Likes

All endpoints **Auth: Required**.

### `GET /likes`
Paginated list of the current user's liked videos (same envelope shape as
watch history, `content[].video` + `content[].likedAt`).

### `POST /likes/{videoId}`
Toggles like/unlike for a video.

Response `200 OK` (video just liked):
```json
{ "success": true, "data": { "liked": true, "likes": 89 } }
```
Calling it again on the same video unlikes it: `{ "liked": false, "likes": 88 }`.

---

## 9. Subscriptions

### `POST /subscriptions/subscribe`
**Auth: Required**

Body (optional): `{ "plan": "premium" }` (defaults to `"basic"`).

Response `200 OK`:
```json
{
  "success": true,
  "message": "Subscription activated",
  "data": {
    "id": "f7a8...-sub1",
    "plan": "basic",
    "active": true,
    "startDate": "2026-08-15T10:15:00",
    "endDate": "2026-09-14T10:15:00"
  }
}
```

### `POST /subscriptions/cancel`
**Auth: Required** — `{ "success": true, "message": "Subscription cancelled" }`

### `GET /subscriptions/me`
**Auth: Required** — list of the user's subscription history, most recent
first (same object shape as above, in an array).

---

## 10. Analytics

### `POST /analytics/track`
**Auth: Optional / Public.** Call once per app load / session from the
frontend (not on every API call) so visit counts reflect real sessions.

Body (optional): `{ "sessionId": "client-generated-uuid" }`

Response: `{ "success": true, "message": "Visit recorded" }`

### `GET /analytics/visits?range=DAILY`
**Auth: ADMIN/ANALYTIC.** `range` = `DAILY | WEEKLY | MONTHLY | SIX_MONTHS | YEARLY`.

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "range": "DAILY",
    "current": {
      "totalVisits": 342,
      "uniqueVisitors": 210,
      "registeredUserVisits": 180,
      "newRegistrations": 14
    },
    "previous": {
      "totalVisits": 298,
      "uniqueVisitors": 190,
      "registeredUserVisits": 150,
      "newRegistrations": 9
    },
    "visitsGrowthPercent": 14.77,
    "registrationsGrowthPercent": 55.56,
    "series": [
      {
        "periodStart": "2026-08-14T00:00:00",
        "totalVisits": 342,
        "uniqueVisitors": 210,
        "registeredUserVisits": 180,
        "newRegistrations": 14
      }
    ]
  }
}
```
`visitsGrowthPercent` / `registrationsGrowthPercent` are `null` when the
previous period had zero activity (undefined percentage change from zero).

### `GET /analytics/registrations?range=SIX_MONTHS`
**Auth: ADMIN/ANALYTIC.** Same payload shape as `/visits` — use
`newRegistrations` / `registrationsGrowthPercent` from the response.

### `GET /analytics/dashboard`
**Auth: ADMIN/ANALYTIC.** Returns all five ranges at once:

```json
{
  "success": true,
  "data": {
    "byRange": {
      "DAILY": { "range": "DAILY", "current": { "...": "..." }, "...": "..." },
      "WEEKLY": { "...": "..." },
      "MONTHLY": { "...": "..." },
      "SIX_MONTHS": { "...": "..." },
      "YEARLY": { "...": "..." }
    }
  }
}
```

---

## 11. Pagination format

Every paginated list endpoint returns:

```json
{
  "content": [ "..." ],
  "page": 0,
  "limit": 20,
  "totalElements": 57,
  "totalPages": 3,
  "hasNext": true,
  "hasPrevious": false,
  "nextCursor": "MDoyMA.MTox.abcDEF123signature"
}
```

To get the next page, send `?cursor=<nextCursor>` — do **not** hand-edit
`page`/`limit` in a URL you're forwarding to a client, since the signed
cursor is what protects the pagination parameters from tampering. If you do
need a specific page/limit directly (e.g. jumping straight to page 5), send
`?page=5&limit=20` on the **first** request instead.

---

## 12. Error format

```json
{
  "success": false,
  "status": 404,
  "error": "Not Found",
  "message": "Video not found: b7e0...-vid1",
  "path": "/api/v1/videos/b7e0...-vid1",
  "timestamp": "2026-08-15T10:20:00.000"
}
```

Validation errors additionally include `fieldErrors`:
```json
{
  "success": false,
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields are invalid",
  "path": "/api/v1/auth/register",
  "fieldErrors": [
    { "field": "password", "message": "password must be at least 6 characters" }
  ],
  "timestamp": "2026-08-15T10:21:00.000"
}
```
