# StreamHub Platform — API Purpose & Storyline

This document exists for one reason: to explain **why** each endpoint exists
and **what real user journey it belongs to**, so anyone touching this
codebase — backend, frontend, or QA — understands the intent behind the
API surface, not just its shape (for that, see `API_DOCUMENTATION.md`).

We follow four personas through the platform:

- **Alice** — a brand-new guest, has never logged in.
- **Bob** — a registered user, has an account but no active subscription.
- **Carol** — a subscribed user, pays for unlimited playback.
- **Dana** — an ADMIN, manages the catalog and watches the analytics dashboard.

---

## 1. Discovery — browsing without an account

**Endpoints:** `GET /categories`, `GET /videos`, `GET /videos/{id}`

**Purpose:** The platform must be browsable with zero friction — no login
wall on the catalog itself. This is deliberate: conversion funnels die when
people can't even see what they'd be signing up for.

**Storyline:** Alice opens the app. The frontend calls `GET /categories` to
build the sidebar/filter chips (this list is Redis-cached — it barely ever
changes, so there's no reason to hit Postgres on every page load). She taps
"Education" and the app calls `GET /videos?categoryId=...&limit=20`, getting
back a page of `VideoSummaryResponse` objects — just enough to render a
grid (title, thumbnail, view/like counts). She taps one video and the app
calls `GET /videos/{id}` for the full detail (description, duration,
category) before showing the player screen. This detail lookup is
Redis-cached for 15 minutes per video, so a video that's trending gets
served almost entirely from cache rather than hammering the database.

---

## 2. Playback & the guest-watch limit — the platform's core mechanic

**Endpoints:** `GET /playback/{videoId}`, `GET /videos/{id}/check-limit`,
`POST /videos/{id}/increment-watch`, `PATCH /videos/{id}/views`

**Purpose:** This is the monetization engine. Anyone who isn't a paying
subscriber gets a small number of free plays before being asked to either
register (if they're a guest) or subscribe (if they're already registered).
The limit is tracked in Redis, keyed by whatever identifies the viewer at
that moment — a logged-in user id, a client-generated session id, or an IP
address as a last resort — and expires after 30 days, so the count doesn't
follow someone forever.

**Storyline:**

1. **Alice hits play (guest, 1st and 2nd video).** The app calls
   `GET /playback/{videoId}` with an `x-session-id` header it generated
   locally (no `Authorization` header — she's not logged in). The server
   sees no valid token, so it treats her as a guest, increments the Redis
   counter for `guest:session:{sessionId}`, and — because the free limit
   (`app.playback.free-limit`, default `2`) hasn't been exceeded — fetches
   the HLS manifest and returns
   `{ "manifestUrl": "...", "locked": false, "freeRemaining": 1 }`. Same
   flow on video #2, this time `freeRemaining: 0`.

2. **Alice hits play (guest, 3rd video).** The counter is now 3, which
   exceeds the limit. The server does **not** bother fetching the manifest
   (no point — it's not going to that be shown) and returns
   `{ "locked": true, "reason": "Account required - create an account to continue watching", "freeRemaining": 0 }`.
   The app shows a "Sign up to keep watching" screen.

3. **Alice registers.** `POST /auth/register` — see section 4.

4. **Bob (registered, not subscribed) hits play.** Same flow, but the
   `Authorization` header is present and verified, so the server tracks him
   by `user:watchcount:{userId}` instead of session/IP — his count follows
   his account across devices, not just one browser. On his third video the
   `reason` text is different: `"Payment required - subscribe to continue watching"`,
   nudging him toward `/subscriptions/subscribe` instead of registration
   (he's already registered).

5. **Carol (subscribed) hits play.** The server checks
   `user.hasActiveSubscription()` first, before touching the guest-tracking
   counters at all. She's unlimited — the manifest comes back immediately
   with `locked: false` and no `freeRemaining` field (there's no limit to
   report).

`GET /videos/{id}/check-limit` exists so the frontend can show "1 free play
left" as a soft warning *before* the user commits to hitting play (it reads
the counter without incrementing it). `POST /videos/{id}/increment-watch`
is a separate, frontend-controlled increment hook, kept deliberately
independent of the playback endpoint for cases where the client wants to
track a "watch" event without going through the manifest-fetch flow (e.g.
embedded/external players). `PATCH /videos/{id}/views` is the raw analytics
view counter (shown on video cards) — it also opportunistically writes a
watch-history row if the caller happens to be authenticated, but it does not
enforce the free-play limit itself; that's `/playback`'s job.

---

## 3. Registration & login — Alice's on-ramp

**Endpoints:** `POST /auth/register`, `POST /auth/login`

**Purpose:** The single on-ramp from "anonymous visitor" to "account with a
JWT". Registration is also the trigger for one analytics signal: every new
account creates a `VisitLog` row with `newRegistration = true`, which is
exactly what powers Dana's registration-growth dashboard (section 6).

**Storyline:** After hitting the free-view wall, Alice fills in a username,
email, and password. `POST /auth/register` hashes her password (BCrypt,
strength 10), creates her `User` row (`role_type = NORMAL_USER` by default),
logs the registration for analytics, and returns a JWT plus her profile.
The app stores the token and attaches it as `Authorization: Bearer <token>`
on every future request — she is now "Bob" in every sense except name.

Login (`POST /auth/login`) is the same shape for a returning user: verify
email exists, verify password matches, issue a fresh token. Both endpoints
are deliberately public — no chicken-and-egg auth requirement to create an
account.

---

## 4. Curating a personal library — Bob's playlists, likes, and history

**Endpoints:** `GET/POST /playlists`, `POST /playlists/{id}/add/{videoId}`,
`DELETE /playlists/{id}/remove/{videoId}`,
`PATCH /playlists/{id}/move/{videoId}/{newPosition}`,
`DELETE /playlists/{id}`, `GET/POST /likes/{videoId}`, `GET/POST /history`

**Purpose:** Once someone has an account, the platform should feel like
*theirs* — a personal library, not just a catalog. These endpoints are all
scoped to the authenticated user (verified via the Spring Security context,
never a client-supplied user id) so there is no way for one user to read or
modify another user's playlist, likes, or history.

**Storyline:** Bob finds a talk he wants to save for later. He creates a
playlist (`POST /playlists`, `{ "title": "Watch Later" }`) and adds the
video to it (`POST /playlists/{playlistId}/add/{videoId}`) — the server
checks he doesn't already have that video in the playlist and assigns it
the next position automatically. Later he reorders it
(`PATCH /playlists/{id}/move/{videoId}/{newPosition}`, positions are
re-indexed server-side so there are never gaps or duplicates) and eventually
removes it (`DELETE /playlists/{id}/remove/{videoId}`).

While watching, Bob taps the heart icon — `POST /likes/{videoId}` toggles a
real per-user `UserLike` row (not just a shared counter), so `GET /likes`
can show him exactly what he's liked, and tapping again correctly *unlikes*
it. Every video he finishes (or that the frontend records a view for) shows
up in `GET /history`, most recent first, which he can also add to manually
via `POST /history/{videoId}` if the frontend wants an explicit "mark as
watched" action.

---

## 5. Subscribing — Carol's upgrade

**Endpoints:** `POST /subscriptions/subscribe`, `POST /subscriptions/cancel`,
`GET /subscriptions/me`

**Purpose:** Converts a rate-limited free user into an unlimited one. This
is the only paid-feature toggle in the system (no real payment gateway is
wired up yet — see the README's "known gaps" note — but the subscription
lifecycle itself is fully modeled).

**Storyline:** Bob gets tired of hitting the two-video wall and taps
"Subscribe". `POST /subscriptions/subscribe` deactivates any prior
subscription he might have had (so subscriptions never silently overlap),
creates a new 30-day subscription, and flips `user.subscribed = true` with
an expiry date — he is now "Carol" for the purposes of `/playback`. He can
check his subscription history at any time via `GET /subscriptions/me`, and
cancel with `POST /subscriptions/cancel` if he changes his mind (this
deactivates the subscription immediately rather than waiting for the expiry
date — a deliberate simplification worth revisiting if you add real
billing).

---

## 6. Running the platform — Dana's analytics dashboard

**Endpoints:** `POST /analytics/track`, `GET /analytics/visits`,
`GET /analytics/registrations`, `GET /analytics/dashboard`

**Purpose:** Product and growth decisions need real numbers: how many
people are actually showing up, how many of them are new faces, and whether
either number is trending up or down. This is restricted to `ADMIN` and
`ANALYTIC` roles — regular users never see it.

**Storyline:** Every time the app loads (once per session, not once per API
call — that distinction matters, otherwise "visits" would just measure API
chattiness instead of real human sessions), it calls `POST /analytics/track`
with a session id if the user isn't logged in. This writes one `VisitLog`
row, tagged with whichever identity signal is available (user id, session
id, or IP as a last resort) so later queries can count *unique* visitors
without double-counting someone who reloads the page five times.

Dana opens the admin dashboard and picks a filter — "today", "this week",
"this month", "last 6 months", or "this year" — which maps directly onto
`GET /analytics/visits?range=DAILY|WEEKLY|MONTHLY|SIX_MONTHS|YEARLY`. For
whichever window she picks, she sees:

- **Total visits** in that window.
- **Unique visitors** (deduplicated by user id, session id, or IP).
- **Registered-user visits** — how many of those visits came from someone
  with an account, versus anonymous guests.
- **New registrations** in that window, and how that compares to the
  *immediately preceding* window of the same length (`visitsGrowthPercent`,
  `registrationsGrowthPercent`) — e.g. "this week vs. last week", "this
  month vs. last month" — so she can tell at a glance whether growth is
  accelerating or stalling.
- A **daily or monthly time series** (`series[]`) for charting the trend
  within that window rather than just a single before/after number.

`GET /analytics/registrations` is the same underlying computation, framed
around the registration numbers specifically (useful when Dana only cares
about signup growth, not raw traffic). `GET /analytics/dashboard` bundles
all five ranges into one call for a single-screen overview, so the frontend
doesn't need five separate round-trips to render Dana's landing dashboard.

---

## 7. Managing the catalog — Dana's admin duties

**Endpoints:** `POST/PUT/DELETE /categories`,
`PATCH /videos/{id}/category`, `DELETE /videos/{id}`

**Purpose:** Someone has to keep the catalog organized. These are the only
write endpoints in the video/category domain that aren't driven by the
automatic filesystem scanner (`VideoScannerService`, which watches
`app.media.source-directory` for new MP4s, converts them to HLS via
FFmpeg, and registers them automatically on startup).

**Storyline:** Dana adds a new "Cooking" category
(`POST /categories { "name": "Cooking" }`). A batch of new videos got
auto-ingested overnight by the scanner but landed with no category, so she
assigns one (`PATCH /videos/{id}/category?categoryId=...`) for each. When a
video needs to be pulled down (rights issue, wrong upload, etc.), she soft-
deletes it (`DELETE /videos/{id}`) — it disappears from every list/detail
endpoint immediately (via the shared `deleted = false` filter every entity
gets from `BaseEntity`) without losing the row for audit purposes.
