# Markit storeBack — Architecture

This document captures the design decisions behind the e-commerce backend. It is intentionally pragmatic: H2 in-memory databases for local development, Caffeine for caching, simulated email, and infrastructure reserved for the Docker/CD profile.

**Stack**: Java 21 · Spring Boot 4.1.1 · Spring Cloud 2025.1.2 · Gradle 9.7.1 · Jackson 3

## 1. System overview

| Service | Responsibility | Data |
|---|---|---|
| `api-gateway` (8080) | Single entry point: JWT validation, rate limiting, circuit breaking, correlation ids | none |
| `user-service` (8081) | Identity: register, login, refresh, profile, RBAC; publishes `UserRegistered` | users, refresh tokens, user outbox |
| `product-service` (8082) | Catalog, stock, search (`LIKE` on name/description/category) | products |
| `order-service` (8083) | Cart, orders, Saga orchestration, outgoing event outbox | carts, cart items, orders, order outbox, idempotency records |
| `notification-service` (8084) | Consumes order + user events, persists and serves notifications | notifications |
| `task-service` (8085) | User-scoped personal task tracker (CRUD + status transitions) | tasks |

Routes: `api-gateway` forwards `/api/users/**`, `/api/products/**`, `/api/orders/**`, `/api/notifications/**`, `/api/tasks/**` to the matching service. Public paths (`/api/users/login`, `/api/users/register`, `GET /api/products/**`) pass through without a token; every other route requires a valid JWT.

## 2. Inter-service communication

### Synchronous (request/response)
- `api-gateway → services` via Spring Cloud Gateway `WebClient` routes.
- `order-service → product-service` via Feign `ProductClient` for stock **decrement/increment**, wrapped in a **circuit breaker + fallback factory**.

### Asynchronous (event-driven)
- **Kafka topics**: `order-events` (order-service producer) and `user-events` (user-service producer).
- `notification-service` consumes both topics and converts events into persisted notifications.

Events are serialized with Jackson and tagged at the entity level — consumers discriminate by fields rather than by class:
- `fromStatus` set → `OrderStatusChanged`
- `reason` set → `OrderCancelled`
- otherwise + `totalPrice` → `OrderCreated`
- `UserRegistered` lives on `user-events`.

## 3. Order integrity: Saga + Outbox + Idempotency

Placing an order spans order-service and product-service, so a naive implementation can lose money or stock in a partial failure. Three mechanisms make it safe:

1. **Saga with compensation** — `order-service` first decrements stock in `product-service` (REST/Feign) and **only then** inserts the order + items + outbox row. Any `RuntimeException` (insufficient stock, service call failure, DB failure) triggers `compensateStock()`, which calls `/stock/increment` for every product already decremented. The result is a single compensating, eventually-consistent order lifecycle: `PENDING → CONFIRMED`, `PENDING → FAILED`, `CONFIRMED → CANCELLED` (cancellation re-increments stock). Stock is never permanently lost or double-spent.

2. **Transactional Outbox** — the event row is written to the outbox table **in the same DB transaction** as the order. `OutboxPublisher` polls un-published rows and sends them to Kafka **outside** the DB transaction; each row is marked published in its own short transaction, so no event is lost on crash and no event is sent before its order is durable. User-service uses an identical pattern for `user-events`.

3. **Idempotency** — `POST /api/orders` accepts an optional `Idempotency-Key` header. If the key matches an existing processed request the stored result is returned; retries triggered by timeouts or the gateway circuit breaker never create duplicate orders.

This is a deliberately **orchestration-style saga** (order-service acts as coordinator). Trade-off: tighter coupling to product-service's contract versus choreography's harder-to-follow flows — acceptable at this scale, and the circuit breaker + fallbacks contain the blast radius.

## 4. Caching

- `product-service` caches product reads with **Caffeine** (`maximumSize=500, expireAfterWrite=300s`), keeping read-heavy catalog traffic off the DB.
- Stock and catalog writes update the cache; `@CacheEvict` on writes keeps staleness bounded.
- Redis is provisioned in the Docker stack but **not yet wired in** — it's the intended move for a distributed cache when the single-node Caffeine hit rate becomes a bottleneck or multi-replica deployment starts.

## 5. Resilience & failure handling

| Concern | Mechanism |
|---|---|
| Downstream failure (order → product) | Feign circuit breaker via `ProductClientFallbackFactory` returning a typed failure; `placeOrder` aborts and compensates |
| Lost Kafka messages | Outbox guarantees at-least-once publish; consumers key logic on `eventId` to stay idempotent |
| Slow/thrashing upstream | Gateway per-route `CircuitBreaker` with a `/fallback` URI |
| Abuse / thundering herd | Gateway `RateLimitGlobalFilter`: in-memory sliding window — 100 req/min per IP, 1000 req/min per user (`X-User-Id`), `429` + `ERR_RATE_LIMITED`, periodic cleanup |
| Startup ordering | Docker Compose `depends_on: … condition: service_healthy`; services self-reconcile once Kafka is up |

## 6. Security

- **JWT at the edge**: the gateway validates access tokens (`JwtService` in `shared-lib`) and injects `X-User-Id` / `X-User-Roles` plus a `X-Request-Id` correlation header on forwarded requests.
- **Downstream enforcement**: services still check ownership and role headers on sensitive endpoints (e.g., `GET /api/orders/{id}` requires owner or `ADMIN`; `PUT /api/orders/{id}/status` and product admin actions require `ADMIN`), so direct exposure of a service port is not a privilege bypass.
- **Tokens**: access token (15 min) + refresh token (7 days), refresh rotation with revocation on logout; `change-password`/profile updates require the current password or a valid refresh token context.
- Passwords are hashed (BCrypt) before storage; errors are returned as structured `ApiError` with machine-readable codes (`ERR_UNAUTHORIZED`, `ERR_FORBIDDEN`, `ERR_RATE_LIMITED`, …).

## 7. Observability

- `X-Request-Id` is generated/injected at the gateway and printed as part of the log pattern (`%X{requestId:-} %5p`) on each request across services via `TraceIdFilter`.
- Actuator exposes `health` (with Kubernetes probes), `info`, `metrics`, and `prometheus` for scraping.
- A structured JSON log encoder is not wired in yet — plaintext with MDC context is used to keep the dependency footprint small; swapping in `logstash-logback-encoder` would be a drop-in change (see future work).

## 8. Data & storage

- **Local/default profile**: H2 in-memory with `MODE=PostgreSQL` (`DATABASE_TO_LOWER=TRUE`) so SQL that runs against Postgres behaves the same on the embedded DB. DB state is ephemeral between restarts.
- **Docker/CD profile**: PostgreSQL 16, one schema per service (`user_service`, `product_service`, `order_service`, `notification_service`, `task_service`), created by Flyway (`flyway.schemas`) with `create-schemas: true`.
- Migrations live under each service's `src/main/resources/db/migration`.

## 9. Testing strategy

- **Unit**: `OrderServiceTest` (compensation, transitions, idempotency), `CartServiceTest`, `NotificationServiceTest`, `TaskServiceTest`, `JwtAuthGlobalFilterTest`, `RateLimitFilterTest`.
- **Integration**: `OrderEventListenerIntegrationTest` (order → notification + dedup + DLQ with `@EmbeddedKafka`), `UserEventPublishingIntegrationTest` (user-service outbox → Kafka), `OrderFlowIntegrationTest` (full order flow with mocked product-client), `TaskFlowIntegrationTest` (task lifecycle + user scoping).
- All tests use `@EmbeddedKafka` + H2 (no Testcontainers yet; see future work).
- **Run everything**: `./gradlew build` (root of the storeBack directory).

## 10. Deployment & CI/CD

- `docker-compose.yml` builds and runs the full stack; `application-docker.yml` profile configures Postgres/Kafka endpoints inside the compose network.
- GitHub Actions (`.github/workflows/`):
  - `backend.yml` — JDK 21 toolchain, cache Gradle, `./gradlew build`, uploads test reports on failure.
  - `frontend.yml` — Node 22, `npm ci`, lint, typecheck, test, production build.

## 11. Known limitations & future work

- **Notifications are DB rows + simulated email** only — no real SMTP/SMS gateway yet. A `sms_notifications` table and provider stub were explicitly deferred.
- **Rate limiter is in-memory per instance** — not shared across replicas; needs Redis-backed counting at scale.
- **No distributed tracing** (Micrometer Tracing/Sleuth) — correlation ids only.
- **No structured JSON logs** yet.
- **Google OAuth is a stub** returning a simulated profile; swap in a real OAuth2 client when a provider is available.
- **Redis is provisioned but unused** — wire it as the cache-of-record when moving to multiple product-service replicas.
- **No Testcontainers integration tests** — only `@EmbeddedKafka` + H2.
- **No Swagger/OpenAPI** documentation endpoints.
- **product-service has no update/delete endpoints** — catalog mutation beyond create/stock-update is pending.

## 12. Key configuration surface

See `README.md` → *Configuration*. The contract between gateway and services (headers `X-User-Id`, `X-User-Roles`, `X-Request-Id`) and the event topics (`order-events`, `user-events`) are the integration points to keep stable.
