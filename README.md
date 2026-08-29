# Task Management Tool — Backend

Spring Boot 3.3.4 REST API (Java 17, Maven, MySQL 8) behind JWT authentication.
It backs the [task-management-frontend](https://github.com/SuryaKiran434/task-management-frontend)
React client, and covers tasks, projects and project membership, comments,
labels, subtasks, notifications, and a per-task activity log.

## Table of Contents

1. [Features](#features)
2. [Architecture](#architecture)
3. [Run Locally](#run-locally)
4. [Configuration](#configuration)
5. [API Reference](#api-reference)
6. [Security](#security)
7. [Data Access and Performance](#data-access-and-performance)
8. [Error Handling](#error-handling)
9. [Testing](#testing)

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
│   │   └── util/         JwtUtil, PasswordValidator, UniqueIdGenerator
│   └── resources/
│       ├── application.properties   datasource, JPA, Hikari, CORS, uploads
│       └── application.yml          profiles and ports, jwt.secret
└── test/                            7 test classes, 47 tests (H2 in-memory)
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
port the frontend's `REACT_APP_API_BASE_URL` default (`http://localhost:8081/api`)
expects.

- API base: `http://localhost:8081/api`
- Swagger UI: **http://localhost:8081/swagger-ui.html**
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

### 4. Test

```bash
./mvnw test
```

47 tests across 7 classes. They run against an in-memory H2 database in MySQL
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
| `dev` | 8081 | **active by default** |
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
| POST | `/users/forgot-password` | issue a reset token |
| POST | `/users/reset-password` | consume a reset token |

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

Paged responses carry `X-Total-Count`, `X-Total-Pages`, `X-Page-Number` and
`X-Page-Size` headers, which CORS exposes to the browser.

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
| `TokenValidationException` | 401 | plain message |
| Spring Security `AuthenticationException` | 401 | plain message |
| `MethodArgumentNotValidException` | 400 | `ErrorResponse` + field errors |
| `ConstraintViolationException` | 400 | `ErrorResponse` + field errors |
| `IllegalArgumentException` | 400 | `ErrorResponse` |
| `JsonProcessingException` | 400 | `ErrorResponse` |
| anything else | 500 | `ErrorResponse` |

Note the gap: `TaskNotFoundException`, `UserNotFoundException`,
`AuthenticationFailedException` and `AuthenticationRequiredException` all extend
`RuntimeException` and have no dedicated handler, so they currently fall through
to the catch-all and surface as **500**, not the 404/401 their names imply.
Worth fixing before anyone builds client behaviour on those status codes.

## Testing

JUnit 5, Mockito, Spring Boot Test and `spring-security-test`, against H2 in
MySQL mode. `./mvnw test` — **47 tests, 7 classes**:

| Class | Covers |
| --- | --- |
| `TaskServiceImplTest` | task service behaviour with mocked repositories |
| `TaskQueryCountIntegrationTest` | query counts for the `@EntityGraph` and aggregate paths |
| `RefactoringIntegrationTest` | Spring context wiring, `PerformanceMonitoringService`, `UserValidationService` |
| `GlobalExceptionHandlerTest` | exception → status mapping |
| `JwtUtilTest` | generation, username/role extraction, validation, refresh (parameterised) |
| `CustomUserDetailsServiceTest` | user lookup and authority mapping |
| `TaskmanagementtoolApplicationTests` | context loads |

CI runs the same command on Java 17 (`.github/workflows/ci.yml`, job
**Backend (Java 17)**), which is a required check on `main`. Dependency updates
arrive weekly through Dependabot (`.github/dependabot.yml`).
