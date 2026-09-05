# Task Management Tool — Backend

Spring Boot 3.5.16 REST API (Java 17, Maven, MySQL 8) behind JWT authentication.
It backs the [task-management-frontend](https://github.com/SuryaKiran434/task-management-frontend)
React client, and covers tasks, projects and project membership, comments,
labels, subtasks, notifications, and a per-task activity log.

## Table of Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Run Locally](#run-locally)
4. [Configuration](#configuration)
5. [API Reference](#api-reference)
6. [Password Reset](#password-reset)
7. [Security](#security)
8. [Data Access and Performance](#data-access-and-performance)
9. [Error Handling](#error-handling)
10. [Testing](#testing)

## Features

- **Tasks** — CRUD with pagination, status/priority filtering, full-text-ish
  search over title and description, soft delete (`deleted_at`) plus restore,
  bulk update and bulk delete, dashboard statistics, and CSV export.
- **Projects and members** — projects owned by a user, with members joined as
  `OWNER`, `EDITOR` or `VIEWER`; tasks can belong to a project.
- **Comments** — flat (non-threaded) per task, authored by a user, editable and deletable.
- **Labels** — user-scoped, many-to-many with tasks, attach/detach endpoints.
- **Subtasks** — ordered checklist items belonging to a task.
- **Notifications** — unread list and counts, mark-one/mark-all read; created by
  the scheduled due-soon and overdue jobs.
- **Activity log** — an append-only record of what changed on a task and by whom
  (14 `ActivityAction` kinds, from `TASK_CREATED` to `LABEL_REMOVED`).
- **Auth and users** — registration, JWT login, refresh, logout via a token
  blacklist, forgot/reset password, avatar upload, and admin role assignment.
  **The password reset is not completable outside the `dev` profile** — see
  [Password reset](#password-reset).
- **Rate limiting** — Bucket4j token bucket in front of the auth endpoints.
- **OpenAPI** — springdoc-generated docs and a Swagger UI.

## Architecture

Layered, with each layer only talking to the one beneath it:

```
                    HTTP request
                         │
        ┌────────────────▼─────────────────┐
        │ RateLimiterFilter (order 1)      │  /api/authenticate, /api/refresh-token
        │   Bucket4j: 10 req/min           │  → 429 when the bucket is empty
        └────────────────┬─────────────────┘
        ┌────────────────▼─────────────────┐
        │ JwtRequestFilter                 │  OncePerRequestFilter, registered
        │   Bearer token → JwtUtil         │  before UsernamePasswordAuthenticationFilter
        │   → CustomUserDetailsService     │
        │   → SecurityContext + authorities│
        └────────────────┬─────────────────┘
        ┌────────────────▼─────────────────┐
        │ SecurityFilterChain              │  stateless, CSRF off, CORS from
        │   permitAll vs authenticated     │  app.cors.allowed-origins
        └────────────────┬─────────────────┘
                         │
   ┌─────────────────────▼──────────────────────┐
   │ controller/   @RestController              │  HTTP mapping, @PreAuthorize,
   │   Auth, Task, User, Project, Comment,      │  @Valid on request bodies
   │   Label, Subtask, Notification             │
   └─────────────────────┬──────────────────────┘
   ┌─────────────────────▼──────────────────────┐
   │ service/       business rules              │  @Transactional boundaries
   │   TaskServiceImpl, UserServiceImpl,        │
   │   ProjectServiceImpl, CommentServiceImpl,  │
   │   LabelServiceImpl, SubtaskServiceImpl,    │
   │   NotificationServiceImpl,                 │
   │   ActivityLogService, TaskExportService,   │
   │   PasswordResetService,                    │
   │   TokenBlacklistService,                   │
   │   ScheduledTaskService                     │
   │                                            │
   │ conversion (entity ⇄ DTO, never exposed    │
   │ entities on write paths):                  │
   │   TaskConversionService                    │
   │   UserConversionService                    │
   │ validation: UserValidationService,         │
   │             PasswordValidator              │
   └─────────────────────┬──────────────────────┘
   ┌─────────────────────▼──────────────────────┐
   │ repository/    Spring Data JPA             │  @EntityGraph, @Query,
   │   TaskRepository, UserRepository, …        │  projections
   └─────────────────────┬──────────────────────┘
   ┌─────────────────────▼──────────────────────┐
   │ model/         JPA entities → MySQL 8      │  schema from ddl-auto=update
   └────────────────────────────────────────────┘
```

### JWT filter chain

`JwtRequestFilter` reads the `Authorization: Bearer …` header, extracts the
subject with `JwtUtil`, loads the user through `CustomUserDetailsService`, and —
only if the token validates — builds a `UsernamePasswordAuthenticationToken`
from the `roles` claim and puts it in the `SecurityContext`. An invalid or
expired token is logged (never the token value itself) and the request continues
unauthenticated, so the `SecurityFilterChain` is what actually rejects it.

Access tokens live 1 hour, refresh tokens 7 days. `POST /api/logout` adds the
token to `TokenBlacklistService`.

### DTO conversion

Controllers accept and return DTOs from `dto/`, not entities.
`TaskConversionService` and `UserConversionService` own the mapping in both
directions, which is what keeps lazy associations from being serialised out of
a controller and keeps password hashes off the wire.

### Scheduled jobs

`ScheduledTaskService` runs under `@EnableScheduling` on the application class:

| Cron | Job | Effect |
| --- | --- | --- |
| `0 0 8 * * *` | `sendDueSoonNotifications` | tasks due tomorrow, not complete, not soft-deleted → `TASK_DUE_SOON` notification |
| `0 0 9 * * *` | `sendOverdueNotifications` | tasks past due, not complete, not soft-deleted → `TASK_OVERDUE` notification |

Both page the query at 1000 rows per run.

### Entity relationships

```
                    ┌──────────┐
      ┌─────────────│   User   │─────────────┐
      │             └────┬─────┘             │
      │ owner            │ @ManyToMany       │ user / assignee
      │                  │ (EAGER,           │
      ▼                  │  @BatchSize 50)   ▼
 ┌─────────┐             ▼            ┌────────────┐
 │ Project │        ┌─────────┐       │    Task    │
 └────┬────┘        │  Role   │       └──┬──┬───┬──┘
      │             └─────────┘          │  │   │
      │ 1:N                              │  │   │ 1:N (cascade, orphanRemoval)
      ▼                                  │  │   ▼
 ┌───────────────┐                       │  │  ┌──────────┐
 │ ProjectMember │──── user ────────────▶│  │  │ Subtask  │
 │  (role enum)  │                       │  │  └──────────┘
 └───────────────┘                       │  │
                                         │  │ 1:N (cascade, orphanRemoval)
 Task ──── N:1 ────▶ Project             │  ▼
                                         │ ┌──────────┐
                                         │ │ Comment  │──── author ──▶ User
                                         │ └──────────┘
                                         │ M:N via task_label
                                         ▼
                                    ┌─────────┐
                                    │  Label  │──── user ──▶ User
                                    └─────────┘

 Notification ──── N:1 ──▶ User      (type enum, is_read, loose task_id)
 ActivityLog  ──── N:1 ──▶ User      (action enum, loose task_id, old/new value)
 UserRole     ──── composite (user_id, role_id), @IdClass UserRoleId
```

Notes worth knowing before you touch these:

- `Task.id` is a **String**, generated by `UniqueIdGenerator`, not an
  auto-increment number. `User.id` is an `int`; every other entity uses `Long`.
- Tasks are **soft-deleted**: `deleted_at` is stamped and every read query
  filters `deletedAt IS NULL`. `POST /api/tasks/{id}/restore` clears it.
- `Notification.taskId` and `ActivityLog.taskId` are plain columns, not foreign
  keys, so a log entry survives its task.
- `User.roles` is an eager `@ManyToMany` — see the `@BatchSize` note below.

### Project structure

```
src/
├── main/
│   ├── java/com/suryakiran/taskmanagementtool/
│   │   ├── config/       CacheConfig, DatabaseConfig, EnumConverterConfig,
│   │   │                 FilterConfig, OpenApiConfig, SecurityConfig, WebMvcConfig
│   │   ├── controller/   REST controllers + TaskResponseHandler
│   │   ├── dto/          request/response DTOs
│   │   ├── exception/    custom exceptions + GlobalExceptionHandler
│   │   ├── filter/       JwtRequestFilter, RateLimiterFilter
│   │   ├── model/        JPA entities and enums
│   │   ├── repository/   Spring Data JPA repositories
│   │   ├── security/     CustomUserDetails
│   │   ├── service/      business logic, conversion, scheduling
│   │   └── util/         JwtUtil, LogSanitizer, PasswordValidator,
│                     UniqueIdGenerator
│   └── resources/
│       ├── application.properties   datasource, JPA, Hikari, CORS, uploads
│       └── application.yml          profiles and ports, jwt.secret
└── test/                           15 test classes, 132 tests (H2 in-memory)
```

## Run Locally

### Prerequisites

- **JDK 17** (`java -version` should report 17)
- **Maven** — not required separately; use the bundled wrapper `./mvnw`
- **MySQL 8** running on `localhost:3306`

### 1. Create the database

The application connects to a schema that must already exist; only the *tables*
are created for you.

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS taskmanager_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

There is **no Flyway or Liquibase** in this project. The schema is managed by
Hibernate through `spring.jpa.hibernate.ddl-auto=update`, which creates and
widens tables from the entity classes on startup but never drops or narrows
anything. Destructive changes have to be applied by hand.

### 2. Set the environment variables

Copy [`.env.example`](.env.example) to `.env` and fill in real values. `.env` is
gitignored — never commit it.

```bash
cp .env.example .env
```

| Variable | Used by | Notes |
| --- | --- | --- |
| `DB_USERNAME` | `spring.datasource.username` | MySQL user |
| `DB_PASSWORD` | `spring.datasource.password` | MySQL password |
| `JWT_SECRET` | `jwt.secret` | HMAC signing key, ≥32 chars. `openssl rand -hex 32` |

Spring Boot does not read `.env` on its own, so export it into the shell before
running:

```bash
set -a && source .env && set +a
```

Alternatively, export the three variables however your shell or IDE run
configuration prefers.

### 3. Run

```bash
./mvnw spring-boot:run
```

The `dev` profile is active by default (`spring.profiles.active: dev` in
`application.yml`), so the API listens on **http://localhost:8081**. That is the
port the frontend's `VITE_API_BASE_URL` default (`http://localhost:8081/api`)
expects. (That variable was `REACT_APP_API_BASE_URL` until the frontend moved
off create-react-app to Vite.)

- API base: `http://localhost:8081/api`
- Swagger UI: **http://localhost:8081/swagger-ui.html**
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

### 4. Test

```bash
./mvnw test
```

132 tests across 15 classes. They run against an in-memory H2 database in MySQL
compatibility mode (`src/test/resources/application-test.properties`), so no
MySQL instance and no environment variables are needed.

### 5. Package

```bash
./mvnw clean package
java -jar target/taskmanagementtool-0.0.1-SNAPSHOT.jar
```

## Configuration

### Profiles and ports

| Profile | Port | Notes |
| --- | --- | --- |
| *(none)* | 8080 | base config in `application.yml` |
| `dev` | 8081 | **active by default**; also the only profile that sets `app.password-reset.expose-otp: true` |
| `prod` | 8082 | |
| `desktop` | — | `SecurityConfig` degrades to `permitAll()` for every request. Local convenience only; never enable it on a reachable host. |

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Key properties

Set in `src/main/resources/application.properties`:

| Property | Default | Purpose |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/taskmanager_db` | MySQL JDBC URL — `jdbc:mysql://<host>:<port>/<schema>` |
| `spring.jpa.hibernate.ddl-auto` | `update` | schema management (no migration tool) |
| `spring.jpa.open-in-view` | `false` | lazy loading does not leak into the view layer |
| `spring.datasource.hikari.maximum-pool-size` | `10` | connection pool ceiling |
| `spring.data.web.pageable.max-page-size` | `50` | caps `?size=` on paged endpoints |
| `app.cors.allowed-origins` | `http://localhost:3000,http://localhost:5173,http://localhost:5174` | comma-separated frontend origins |
| `app.upload.dir` | `${user.home}/taskmanager-uploads` | avatar upload target |
| `spring.servlet.multipart.max-file-size` | `5MB` | avatar size ceiling |
| `app.password-reset.expose-otp` | `false` (`true` under `dev`) | whether `/users/forgot-password` returns the OTP in its response body — set in `application.yml`, not `application.properties` |

Caching is enabled (`@EnableCaching`) with an in-memory
`ConcurrentMapCacheManager`; `CacheConfig` names the caches and carries a
commented-out Redis manager for when a single JVM is no longer enough.

## API Reference

All paths are relative to `/api`. Everything except the public endpoints below
requires `Authorization: Bearer <token>`.

### Public

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/authenticate` | email + password → `{ token, refreshToken }` |
| POST | `/refresh-token` | refresh token → new access token |
| POST | `/logout` | blacklist a token |
| POST | `/users/register` | self-registration |
| POST | `/users/forgot-password` | issue a reset OTP — returned in the body **only under the `dev` profile**, see [Password reset](#password-reset) |
| POST | `/users/reset-password` | consume an OTP and set a new password |

Swagger UI and `/v3/api-docs/**` are also public.

### Tasks — `/tasks`

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/tasks` | paged; own tasks, or all tasks for an admin |
| POST | `/tasks` | create |
| GET | `/tasks/{id}` | |
| PUT | `/tasks/{id}` | owner or `ROLE_ADMIN` |
| DELETE | `/tasks/{id}` | soft delete; owner or `ROLE_ADMIN` |
| POST | `/tasks/{id}/restore` | undo a soft delete |
| GET | `/tasks/filter` | `?status=&priority=` |
| GET | `/tasks/search` | `?q=` over title and description |
| GET | `/tasks/stats` | dashboard counts |
| GET | `/tasks/{id}/activity` | activity log for one task |
| POST | `/tasks/bulk-update` | set status/priority on many ids |
| POST | `/tasks/bulk-delete` | soft-delete many ids |
| GET | `/tasks/user/{userId}` | `ROLE_ADMIN` only |
| GET | `/tasks/export` | CSV of the caller's tasks |
| GET | `/tasks/home` | a static greeting string; not used by the frontend |

The paged endpoints (`/tasks`, `/tasks/filter`, `/tasks/search`,
`/tasks/user/{userId}`) return a **plain JSON array**, not a Spring `Page`
envelope — paging metadata travels in `X-Total-Count`, `X-Total-Pages`,
`X-Page-Number` and `X-Page-Size` headers, which CORS exposes to the browser.
Standard `?page=` and `?size=` query parameters apply, capped at 50 rows.

### Projects — `/projects`

`POST /projects`, `GET /projects`, `GET /projects/{id}`, `PUT /projects/{id}`,
`DELETE /projects/{id}`, `POST /projects/{id}/members`,
`DELETE /projects/{id}/members/{userId}`.

### Comments — `/tasks/{taskId}/comments`

`GET`, `POST`, `PUT /{commentId}`, `DELETE /{commentId}`.

### Subtasks — `/tasks/{taskId}/subtasks`

`GET`, `POST`, `PUT /{subtaskId}`, `DELETE /{subtaskId}`.

### Labels — `/labels`

`GET /labels`, `POST /labels`, `PUT /labels/{id}`, `DELETE /labels/{id}`,
`POST /labels/tasks/{taskId}/add/{labelId}`,
`DELETE /labels/tasks/{taskId}/remove/{labelId}`.

### Notifications — `/notifications`

`GET /notifications`, `GET /notifications/unread`, `GET /notifications/count`,
`PUT /notifications/{id}/read`, `PUT /notifications/read-all`.

### Users — `/users`

| Method | Path | Access |
| --- | --- | --- |
| GET | `/users` | `ROLE_ADMIN` |
| POST | `/users` | `ROLE_ADMIN` — create a user directly |
| GET | `/users/me` | any authenticated user |
| GET | `/users/{id}` | admin or self |
| PUT | `/users/{id}` | admin or self |
| DELETE | `/users/{id}` | admin or self |
| POST | `/users/me/avatar` | multipart upload |
| POST | `/users/{id}/assign-admin` | `ROLE_ADMIN` |
| POST | `/users/{id}/remove-admin` | `ROLE_ADMIN` |

## Password Reset

**The reset flow cannot be completed outside the `dev` profile.** There is no
email delivery in this project yet, so nothing sends the user their OTP.

`POST /api/users/forgot-password` generates a 6-digit OTP, stores it in
`PasswordResetService` for 15 minutes, and returns `200` with a fixed message
regardless of whether the address has an account. Whether the OTP itself comes
back in the response body is controlled by `app.password-reset.expose-otp`,
which is **`false` everywhere except the `dev` profile**. With it off, the OTP
is generated and stored but never leaves the server, so `POST
/api/users/reset-password` can never be given a valid token by a real user.

That is deliberate rather than an oversight. Both endpoints are `permitAll`, so
returning the OTP means anyone who can name an email address can read its OTP
and hand it straight back to `/reset-password` — an unauthenticated takeover of
any account whose address is known. Until a mailer exists, the flow is left
non-functional in deployed environments rather than open to everyone.
`UserController` logs a warning at startup whenever the echo is on, and
`ForgotPasswordOtpExposureTest` covers both settings.

**To finish this feature**, wire a mailer into `UserController.forgotPassword`
so the OTP is sent to the address instead of returned, and leave
`expose-otp: false`.

## Security

- **Stateless JWT.** No server session; `SessionCreationPolicy.STATELESS` and
  anonymous authentication disabled.
- **Passwords** are BCrypt-hashed. `PasswordValidator` requires at least 8
  characters with an uppercase letter, a lowercase letter, a digit, and a
  special character.
- **Authorisation** is method-level (`@EnableMethodSecurity`). Ownership checks
  go through the SpEL expression
  `@taskService.isTaskOwner(#id, authentication?.principal?.id)`, so an admin or
  the owner passes and nobody else does.
- **Rate limiting.** `FilterConfig` puts a Bucket4j bucket of 10 requests per
  minute in front of `/api/authenticate` and `/api/refresh-token`; over the
  limit returns `429` with an `X-Rate-Limit-Retry-After-Seconds` header. The
  bucket is per-instance and shared across callers, not per-IP.
- **CORS** allows only the origins in `app.cors.allowed-origins`, with
  credentials enabled.
- **Secrets** come from the environment. `application.properties` and
  `application.yml` reference `${DB_USERNAME}`, `${DB_PASSWORD}` and
  `${JWT_SECRET}` and contain no values; `.env` and `application-local.*` are
  gitignored.

## Data Access and Performance

The read paths were reworked to stop the ORM from turning one logical read into
dozens of round trips.

- **`@EntityGraph` on the task read paths.** Every `TaskRepository` finder that
  returns tasks for display — `findAll`, `findById`, `findByUser`,
  `findByIdAndUser`, `findByStatusAndPriority`, `findByUserAndStatusAndPriority`,
  `searchByUser` — declares
  `@EntityGraph(attributePaths = {"project", "user", "assignee"})`, so those
  three to-one associations are fetched in the same join instead of one SELECT
  per row. **A task page went from 44 queries to 4.**
- **One aggregate query for dashboard stats.** `getStatsByUser` computes total,
  to-do, in-progress, complete and overdue in a single pass using
  `SUM(CASE WHEN … THEN 1 ELSE 0 END)` conditional aggregation, **replacing five
  separate COUNT queries** over the same rows. The projection getters are boxed
  because `SUM` returns `NULL` for a user with no tasks.
- **Batched bulk operations.** `bulkUpdateTasks` and `bulkDeleteTasks` load the
  target rows with a single `id IN (:ids)` query scoped to the caller and write
  them back through one `saveAll`, rather than a find-then-save per id.
- **`@BatchSize(50)` on `User.roles`.** That association is eager because
  several endpoints serialise `User` directly; the batch size collapses the
  eager load into one `IN` query per 50 users instead of one per user.
- **Composite index `(user_id, deleted_at, status)`.** `idx_task_user_active_status`
  on `task` matches the shape of nearly every task query — always scoped to a
  user, always excluding soft-deleted rows, usually filtered by status — so the
  three predicates are served by one index rather than a single-column index
  plus a filter.

`TaskQueryCountIntegrationTest` asserts these query counts, so a regression that
reintroduces an N+1 fails the build rather than quietly slowing things down.

Also in play: `spring.jpa.open-in-view=false` (lazy loading cannot escape a
service transaction), HikariCP with a 10-connection ceiling, response
compression above 1&nbsp;KB, and paging capped at 50 rows.

## Error Handling

`GlobalExceptionHandler` (`@ControllerAdvice`) maps exceptions onto status codes
and, for most of them, an `ErrorResponse` body of `{ status, message }` —
validation failures additionally carry a field-error map.

| Exception | Status | Body |
| --- | --- | --- |
| `ResourceNotFoundException` | 404 | `ErrorResponse` |
| `NoTasksFoundException` | 404 | `ErrorResponse` |
| `TaskNotFoundException` | 404 | `ErrorResponse` |
| `UserNotFoundException` | 404 | `ErrorResponse` |
| `TokenValidationException` | 401 | plain message |
| Spring Security `AuthenticationException` | 401 | plain message |
| `AuthenticationFailedException` | 401 | plain message |
| `AuthenticationRequiredException` | 401 | plain message |
| `MethodArgumentNotValidException` | 400 | `ErrorResponse` + field errors |
| `ConstraintViolationException` | 400 | `ErrorResponse` + field errors |
| `IllegalArgumentException` | 400 | `ErrorResponse` |
| `JsonProcessingException` | 400 | `ErrorResponse` |
| anything else | 500 | `ErrorResponse` |

`TaskNotFoundException`, `UserNotFoundException`,
`AuthenticationFailedException` and `AuthenticationRequiredException` used to
have no dedicated handler and fell through to the catch-all as **500**. Each
now maps to the status its name implies, and `ExceptionStatusCodeIntegrationTest`
asserts all of them end to end.

## Testing

JUnit 5, Mockito, Spring Boot Test and `spring-security-test`, against H2 in
MySQL mode. `./mvnw test` — **132 tests, 15 classes**:

| Class | Tests | Covers |
| --- | --- | --- |
| `JwtUtilTest` | 20 | generation, username/role extraction, validation, refresh (parameterised) |
| `EmailInLogsSweepTest` | 19 | every request-scoped path that logs a user, asserting no plaintext address reaches a record |
| `EmailMaskingTest` | 17 | `maskEmail` across local-part lengths, missing `@`, and non-address input |
| `ExceptionStatusCodeIntegrationTest` | 11 | exception → HTTP status end to end through the handler |
| `TaskServiceImplTest` | 10 | task service behaviour with mocked repositories |
| `TaskQueryCountIntegrationTest` | 10 | query counts for the `@EntityGraph` and aggregate paths |
| `GlobalExceptionHandlerTest` | 9 | exception → status mapping |
| `ForgotPasswordOtpExposureTest` | 9 | that `/forgot-password` returns the OTP only when `app.password-reset.expose-otp` is on, and that a known and an unknown address are indistinguishable either way |
| `UserControllerSecurityTest` | 7 | that a non-admin cannot grant itself a role through a profile update |
| `LogSanitizerTest` | 6 | CR/LF and control-character stripping, truncation |
| `TokenBlacklistServiceTest` | 4 | revocation on logout, expiry eviction |
| `RefactoringIntegrationTest` | 4 | Spring context wiring, `PerformanceMonitoringService`, `UserValidationService` |
| `BearerOnlyAuthenticationTest` | 3 | that only `Authorization: Bearer` is accepted |
| `CustomUserDetailsServiceTest` | 2 | user lookup and authority mapping |
| `TaskmanagementtoolApplicationTests` | 1 | context loads |

### CI

`.github/workflows/ci.yml`, job **Backend (Java 17)** — a required check on
`main`. It runs `./mvnw -B test` on Temurin 17, uploads the surefire reports,
renders the JaCoCo coverage numbers into the job summary, uploads
`target/site/jacoco/jacoco.xml`, and runs `./mvnw sonar:sonar`
(`continue-on-error`, so a SonarCloud outage cannot fail the required check).
Sonar's configuration lives in `pom.xml`, not a `sonar-project.properties`,
because `sonar-maven-plugin` does not read that file.

Two other workflows sit alongside it:

| Workflow | Trigger | Does |
| --- | --- | --- |
| `dependabot-auto-merge.yml` | `pull_request` from `dependabot[bot]` | queues auto-merge for patch/minor updates, so they land once the required check passes; majors are left for a human |
| `slack-notify.yml` | push to any branch | posts commit metadata to a Slack webhook |

Dependency updates arrive weekly through Dependabot
(`.github/dependabot.yml`), **grouped into one PR per ecosystem** (maven and
github-actions) covering that week's minor and patch bumps. Library majors are
ignored; action majors are not, because GitHub retires old action runtimes.
