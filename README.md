# StreamHub Platform — Backend (Java 21 / Spring Boot)

A complete rewrite of the original NestJS video-streaming backend as a single,
well-structured **Java 21 + Spring Boot 3.3 monolith**, built for production
use.

This project intentionally **fixes every inconsistency** that was documented
in the original codebase review (mismatched free-view limits, unregistered
`LikesController`, expired-token acceptance in playlists, overlapping
subscriptions, numeric-vs-UUID id inconsistency, duplicate JWT configs, etc).
Each fix is called out in the Javadoc of the relevant class and summarized in
[`ISSUES_FIXED.md`](#issues-fixed-vs-the-original-codebase) below.

---

## 1. Tech stack

| Concern            | Choice                                             |
|---------------------|-----------------------------------------------------|
| Language / runtime  | Java 21                                              |
| Framework           | Spring Boot 3.3.5                                    |
| Build tool          | Maven                                                |
| Database            | PostgreSQL                                           |
| Migrations          | Flyway                                               |
| Cache / counters    | Redis (Spring Data Redis)                            |
| Auth                | Spring Security + JWT (jjwt)                         |
| ORM                 | Spring Data JPA / Hibernate                          |
| API docs            | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Boilerplate removal | Lombok                                               |
| Video processing    | FFmpeg via `ProcessBuilder` (MP4 → HLS + thumbnail)  |

> **Note on this delivery:** this environment does not have network access to
> Maven Central, so the project could not be compiled/tested here. The code
> was written carefully and consistently (package-by-package, matching
> Spring Boot 3.3 / Jakarta EE 10 APIs), but you should run
> `mvn clean verify` locally before deploying, and fix any small issues Maven
> surfaces (dependency version bumps, IDE-specific formatting, etc).

---

## 2. Project structure

The codebase is a **modular monolith** — one deployable Spring Boot app,
cleanly split into feature packages so any engineer can find what they need
quickly:

```
src/main/java/com/streamhub/platform/
├── StreamhubApplication.java        # main() entry point
├── common/                          # shared infrastructure, used by every module
│   ├── entity/BaseEntity.java       # id (UUID), createdAt, updatedAt, soft-delete
│   ├── pagination/                  # PaginationService + signed cursor (CursorService)
│   ├── aop/                         # LoggingAspect (method, path, ms, cache/db source)
│   ├── cache/RedisCacheService.java # explicit Redis read/write helper
│   ├── security/                    # JwtService, JwtAuthenticationFilter, SecurityConfig
│   ├── exception/                   # ApiException hierarchy + GlobalExceptionHandler
│   ├── response/                    # ApiResponse<T> / ErrorResponse envelopes
│   └── config/                      # CORS, Redis cache manager, OpenAPI
├── user/                            # User entity (NOT a BaseEntity — see below), UserService
├── auth/                            # register / login
├── subscription/                    # 30-day subscriptions, one active at a time
├── category/                        # video categories (ADMIN-managed, cached)
├── video/                           # Video entity, HLS scanner/converter, likes-count, views
├── playback/                        # the guest-watch-limit engine (see storyline doc)
├── playlist/                        # user playlists
├── watchhistory/                    # per-user watch history
├── like/                            # per-user video likes (properly wired this time)
└── analytics/                       # visit/registration analytics for ADMIN/ANALYTIC roles

src/main/resources/
├── application.properties           # single global config file (env-var overridable)
└── db/migration/                    # Flyway: V1__init_schema.sql, V2__seed_data.sql
```

### Why `User` doesn't extend `BaseEntity`

Every other entity extends `common.entity.BaseEntity`, which provides a
random UUID primary key, `createdAt`/`updatedAt` timestamps, and a
`deleted` soft-delete flag (transparently filtered out of every query via
Hibernate's `@SQLRestriction`).

`User` is intentionally different, per the product requirement: it has an
internal numeric `id` (fast joins/indexes, **never returned by the API**)
plus a public-facing random `uid` that is what every response and the
frontend actually see. This means a raw sequential database id is never
leaked through the API — see `UserResponse`.

### Roles

Roles are a plain enum column, not a separate table:

```java
public enum RoleType { ADMIN, ANALYTIC, NORMAL_USER }
```

stored on `users.role_type`. Spring Security authorities are derived from it
(`ROLE_ADMIN`, `ROLE_ANALYTIC`, `ROLE_NORMAL_USER`), and endpoints use
`@PreAuthorize("hasRole('ADMIN')")` (or `hasAnyRole(...)`) for anything
role-restricted.

---

## 3. Common infrastructure (used identically by every module)

### 3.1 Pagination + URL-tamper protection

Every list endpoint (`/videos`, `/history`, `/likes`, ...) shares the same
`PaginationService`. Clients can pass `page`/`limit` directly for a first
request, **or** send back the `nextCursor` value from a previous response.
`nextCursor` is an HMAC-SHA256-signed token (see `CursorService`, keyed by
`app.pagination.salt`) — if a client edits the page/limit values embedded in
it, the signature check fails and the request is rejected with
`400 Bad Request`. This is the "salt added to the URL so no one can
interfere with the id/pagination side" requirement.

### 3.2 Spring AOP request logging

`common.aop.LoggingAspect` wraps **every** controller method
(`execution(* com.streamhub.platform..controller..*.*(..))`) and logs:

```
[API] GET /api/v1/videos/3fa8...  -> VideoController.getById(..) | 4ms | source=REDIS_CACHE | status=OK
[API] GET /api/v1/videos/3fa8...  -> VideoController.getById(..) | 41ms | source=DATABASE | status=OK
```

`source` (`REDIS_CACHE` / `DATABASE` / `MIXED`) comes from
`ResponseSourceContext`, a request-scoped `ThreadLocal` that
`RedisCacheService` and read-through services set as they run — so the log
line tells you, for every single request, whether the response was served
from cache or hit the database.

### 3.3 Redis

- `app.redis.cache-ttl-seconds` in `application.properties` is the **one**
  global cache TTL used by every `@Cacheable` region and every manual
  `RedisCacheService` write.
- `RedisCacheService` is the explicit get/set/increment wrapper used by
  guest-watch counters and video-detail caching.
- Spring's `CacheManager` (see `RedisConfig`) backs the declarative
  `@Cacheable`/`@CacheEvict` annotations used for categories.

### 3.4 CORS

`app.cors.frontend-url` in `application.properties` (comma-separated for
multiple environments) is the single source of truth for allowed origins —
configured once in `CorsConfig`, applied globally.

### 3.5 Security / JWT

- One `JwtService`, one secret, one expiry (`app.security.jwt.secret`,
  `app.security.jwt.expiration-ms`) — the original codebase accidentally had
  two different `JwtService` instances with two different expiries; that
  bug class is structurally impossible here since there's only one bean.
- `JwtAuthenticationFilter` **always verifies** signature + expiry (never a
  "decode without verify" shortcut).
- Per-endpoint auth requirements are declared once in `SecurityConfig`
  (coarse-grained) plus `@PreAuthorize` (fine-grained, e.g. ADMIN-only
  category/video management).

---

## 4. Getting started

### 4.1 Prerequisites

- JDK 21
- Maven 3.9+
- PostgreSQL 15+
- Redis 7+
- FFmpeg (on `PATH`, or set `FFMPEG_PATH`) — only required if you want the
  automatic MP4 → HLS conversion on startup

### 4.2 Quick start with Docker (recommended for local dev)

```bash
docker run -d --name streamhub-postgres -e POSTGRES_USER=streamhub \
  -e POSTGRES_PASSWORD=streamhub -e POSTGRES_DB=streamhub -p 5432:5432 postgres:16

docker run -d --name streamhub-redis -p 6379:6379 redis:7
```

### 4.3 Configure

All configuration lives in `application.properties` and is overridable via
environment variables (see the table below). Nothing needs to be edited for
local development against the containers above — just set a real
`JWT_SECRET`, `PAGINATION_SALT` before deploying anywhere real.

| Env var                 | Purpose                                   | Default (dev only) |
|--------------------------|--------------------------------------------|---------------------|
| `DB_URL`                | JDBC URL                                   | `jdbc:postgresql://localhost:5432/streamhub` |
| `DB_USERNAME` / `DB_PASSWORD` | DB credentials                       | `streamhub` / `streamhub` |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection                       | `localhost` / `6379` |
| `FRONTEND_URL`          | CORS origin(s), comma-separated            | `http://localhost:5173` |
| `JWT_SECRET`            | HMAC signing key (256-bit+)                | dev placeholder — **change in prod** |
| `JWT_EXPIRATION_MS`     | Token lifetime in ms                       | `604800000` (7 days) |
| `PAGINATION_SALT`       | HMAC key for pagination cursors            | dev placeholder — **change in prod** |
| `CACHE_TTL_SECONDS`     | Global Redis cache TTL                     | `3600` |
| `PLAYBACK_FREE_LIMIT`   | Free videos before a guest/free user is locked out | `2` |
| `PLAYBACK_GUEST_TTL_DAYS` | How long a guest's free-view count is remembered | `30` |
| `MEDIA_SOURCE_DIR`      | Where raw MP4s are dropped for conversion  | `./media/incoming` |
| `MEDIA_HLS_DIR`         | Where generated HLS output is written      | `./media/hls` |
| `FFMPEG_PATH`           | Path to the ffmpeg binary                  | `ffmpeg` |

### 4.4 Build & run

```bash
mvn clean package
java -jar target/streamhub-platform.jar
```

or for local development:

```bash
mvn spring-boot:run
```

Flyway runs automatically on startup and creates the full schema plus seed
data (an `admin` and an `analytics` account — see below).

### 4.5 Default accounts (seeded by `V2__seed_data.sql`)

| Role      | Email                        | Password        |
|-----------|-------------------------------|------------------|
| ADMIN     | `admin@streamhub.local`       | `ChangeMe123!`   |
| ANALYTIC  | `analytics@streamhub.local`   | `ChangeMe123!`   |

**Change these passwords immediately in any real deployment** (there is no
password-reset endpoint yet — update `users.password` directly with a fresh
BCrypt hash, or add one).

### 4.6 API docs

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Full endpoint reference with example responses: [`API_DOCUMENTATION.md`](./API_DOCUMENTATION.md)
- Endpoint purpose + user-journey storyline: [`API_STORYLINE.md`](./API_STORYLINE.md)

---

## 5. Testing

Only a placeholder test class is included (`StreamhubApplicationTests`).
Before going to production, add:

1. **Unit tests** for `PlaybackService`, `GuestTrackingService`,
   `SubscriptionService`, and `PlaylistService` (the highest-value business
   logic).
2. **Integration tests** with [Testcontainers](https://testcontainers.com)
   spinning up real PostgreSQL + Redis containers, covering the
   register → login → playback-limit → subscribe → unlimited-playback flow
   end to end (mirrors the Alice/Bob/Carol storyline in
   `API_STORYLINE.md`).
3. A **Flyway migration test** that runs `flyway migrate` against a fresh
   container on every CI run.

---

## 6. Issues fixed vs. the original codebase

| # | Original issue | Fix in this codebase |
|---|-----------------|------------------------|
| 1 | Free-view limit hardcoded to `10` in one service and `2` in another | Single source of truth: `GuestTrackingService` + `app.playback.free-limit` |
| 2 | `LikesController`/`LikeService` existed but were never registered — `/likes` was dead code | Properly wired `LikeController` + `LikeService`, backed by real `UserLike` rows |
| 3 | Playlist auth used `decodeToken` (no signature/expiry check) — expired tokens accepted | All auth now goes through the verified Spring Security context |
| 4 | Two `JwtService`/`JwtModule` registrations with different expiries | One `JwtService` bean, one secret, one expiry |
| 5 | `SubscriptionService.createSubscription` never deactivated prior subscriptions — overlaps possible | `subscribe()` deactivates all existing active subscriptions first |
| 6 | `Video` entity used a numeric auto-increment id while every other entity used UUID | Standardized on UUID everywhere via `BaseEntity` |
| 7 | Duplicate `@Column` decorators on `views`/`likes` in the original TypeORM entity | Not applicable in JPA — clean single-column mappings |
| 8 | `PlaybackService` fetched the manifest even for locked responses | Manifest is only fetched when the response is actually unlocked |
| 9 | Guest IP tracking silently reused the session-id Redis key pattern | Dedicated `guest:ip:{ip}` key namespace |
| 10 | `moveVideo` accepted an unvalidated `newPosition` | Validated (`>= 0`, clamped to list bounds) before applying |
| 11 | Redundant re-extraction of the current user in watch-history/likes controllers | Current user resolved once from the Spring Security context via `UserService.getCurrentUser()` |
| 12 | No visibility into whether a response was served from cache or DB | `LoggingAspect` + `ResponseSourceContext` log this on every request |

---


