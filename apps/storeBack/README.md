# Markit — Microservices E-Commerce Backend (storeBack)

A pragmatic microservices e-commerce backend: **6 services** (including a task tracker), **Saga + Outbox + Idempotency** for order integrity, Caffeine caching, async notifications over Kafka, and a Spring Cloud Gateway front door with JWT auth, circuit breakers, and rate limiting.

**Built with**: Java 21 · Spring Boot 4.1.1 · Spring Cloud 2025.1.2 · Gradle 9.7.1

## Services

| Service               | Port | Technology / Notes                                         |
|-----------------------|:----:|-----------------------------------------------------------|
| `api-gateway`         | 8080 | Spring Cloud Gateway — JWT auth, rate limiting, circuit breakers, routing |
| `user-service`        | 8081 | Spring Boot + JPA — registration, JWT access/refresh, OAuth stub, publishes `user-events` |
| `product-service`     | 8082 | Spring Boot + JPA + Caffeine — catalog, stock, `LIKE` search |
| `order-service`       | 8083 | Spring Boot + JPA — cart, orders (Saga + Outbox + idempotency), owns `order-events` |
| `notification-service`| 8084 | Spring Boot + JPA — consumes `order-events` + `user-events`, stores/paginates notifications |
| `task-service`        | 8085 | Spring Boot + JPA — user-scoped task tracker (CRUD + status), no events |

### Runtime dependencies
- **Kafka** (topics: `order-events`, `user-events`)
- **PostgreSQL** for the Docker/CD profile (each service gets its own schema); local profile uses **H2 in-memory** (MODE=PostgreSQL)
- **Redis** runs in the stack but is **not wired into any service yet** (reserved for distributed caching)

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                   api-gateway :8080                      │
                    │  JWT validate · rate limit · circuit-break · route      │
                    └───┬──────┬──────────┬──────────┬────────────┬──────────┘
                        │      │          │          │            │ X-User-Id / X-User-Roles
                ┌───────▼──┐ ┌─▼────────┐ ┌───────▼───┐ ┌──────▼──────────┐ ┌───▼────────┐
                │  users   │ │ products │ │  orders   │ │  notificatns    │ │   tasks    │
                │  :8081   │ │  :8082   │ │  :8083    │ │    :8084        │ │   :8085    │
                └────┬─────┘ └────┬─────┘ └─────┬─────┘ └────────┬────────┘ └────────────┘
                     │            │              │                │
                     │            │         REST/Feign           │
                     │            └──────────┬───┘                │
                     └───────────────────────┤                    │
   user-events ──────────────────────────────┘    order-events ───┘
                                    KAFKA ──────────────────────────
```

## Architecture highlights

- **Saga orchestration**: an order is created only after product stock is decremented; any failure triggers compensating stock restores, and cancelling an order re-increments stock for every line item.
- **Transactional Outbox**: both `order-events` and `user-events` are written to outbox tables in the same DB transaction as the write, then published to Kafka by polling publishers (crash-safe, no lost events).
- **Idempotency**: duplicate/aged order requests are safe — keyed by `Idempotency-Key` header (or legacy `X-Idempotency-Key`) + cart hash + user; keys expire after a configurable TTL.
- **Async notifications**: order + user-registration events fan out to `notification-service`, with event dedup and DLQ handling.
- **JWT propagation**: the gateway validates tokens and injects `X-User-Id` / `X-User-Roles`; downstream services enforce ownership and RBAC.
- **Task tracker**: user-scoped CRUD for personal tasks (separate service, no event publishing).

## Prerequisites

- Java 21
- Gradle 9.x (or use the bundled `./gradlew`)
- Docker + Docker Compose (for the full stack)

## Run locally (dev, zero-config DB)

Services default to an embedded H2 database, so you only need Kafka:

```bash
docker compose up -d kafka
```

Then start services (each in its own terminal, or run them all as background tasks):

```bash
./gradlew :api-gateway:bootRun
./gradlew :user-service:bootRun
./gradlew :product-service:bootRun
./gradlew :order-service:bootRun
./gradlew :notification-service:bootRun
./gradlew :task-service:bootRun
```

All requests go through the gateway at `http://localhost:8080`.

## Run the full stack (Docker)

Containerized services use PostgreSQL + Kafka and auto-start only after infra is healthy:

```bash
docker compose up --build
```

- Gateway: `http://localhost:8080`
- Service health: `http://localhost:808x/actuator/health`
- Kafka: `localhost:9092`, Postgres: `localhost:5432`, Redis: `localhost:6379`

## Quick start (curl)

```bash
GW=http://localhost:8080

# 1. Register
curl -s -X POST $GW/api/users/register -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","name":"Alice","password":"Secret123","address":"1 Main St"}'

# 2. Login → token
TOKEN=$(curl -s -X POST $GW/api/users/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Secret123"}' | jq -r '.accessToken')

# 3. Browse products (seeded by DataSeeder)
curl -s $GW/api/products -H "Authorization: Bearer $TOKEN" | jq '.items[].name'

# 4. Get product details + add to cart
PRODUCT_ID=$(curl -s "$GW/api/products" -H "Authorization: Bearer $TOKEN" | jq -r '.items[0].id')
curl -s -X POST $GW/api/orders/cart/add -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":2}"

# 5. Place an order (Idempotency-Key optional)
CART=$(curl -s $GW/api/orders/cart -H "Authorization: Bearer $TOKEN" | jq -r '.id')
curl -s -X POST $GW/api/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: order-alpha-1' \
  -d "{\"cartId\":\"$CART\",\"shippingAddress\":\"1 Main St\"}"

# 6. Check notifications (order confirmation arrives via Kafka)
curl -s "$GW/api/notifications?page=0&pageSize=20" -H "Authorization: Bearer $TOKEN"

# 7. Create a task
curl -s -X POST $GW/api/tasks -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Pack order","description":"Prepare shipping box","priority":"HIGH"}'
```

Need `jq` for the helpers above — or paste the JSON responses and extract ids manually.

## API surface (via gateway)

| Method & Path              | Auth | Notes |
|----------------------------|------|-------|
| `POST /api/users/register` | —    | Creates user, publishes `UserRegistered` |
| `POST /api/users/login`    | —    | Returns access + refresh token |
| `POST /api/users/refresh`  | —    | Rotate refresh token |
| `POST /api/users/logout`   | ✓    | Revoke refresh token |
| `GET/PUT/DELETE /api/users/{userId}` | ✓ (owner/ADMIN) | Profile |
| `POST /api/users/change-password` | ✓ | |
| `GET /api/users/admin/all` , `POST /api/users/admin/{userId}/role` | ADMIN | |
| `GET/POST /api/products`   | GET public · POST ADMIN | `?search=` / `?category=` |
| `GET /api/products/{id}`, `PUT /api/products/{id}/stock`, `GET /api/products/{id}/stock` | GET public · rest ADMIN | |
| `POST /api/products/{id}/stock/decrement` , `…/stock/increment` | internal | Used by `order-service` |
| `POST /api/orders/cart/add` , `GET /api/orders/cart` , `POST /api/orders/cart/remove` , `POST /api/orders/cart/clear` | ✓ | Cart per user |
| `POST /api/orders`         | ✓ | Place order (`Idempotency-Key` header optional, `X-Idempotency-Key` accepted as fallback) |
| `GET /api/orders` , `GET /api/orders/{orderId}` | ✓ | Own orders (owner/ADMIN) |
| `PUT /api/orders/{orderId}/status` | ADMIN | Cancellation triggers compensation |
| `GET/POST /api/notifications`   | ✓ | `?page=&pageSize=&status=` |
| `GET /api/notifications/{notifId}` | ✓ | |
| `GET /api/tasks` , `POST /api/tasks` | ✓ | User-scoped task list + create |
| `GET /api/tasks/{taskId}`, `PUT /api/tasks/{taskId}`, `DELETE /api/tasks/{taskId}` | ✓ | Own tasks only |
| `PATCH /api/tasks/{taskId}/status` | ✓ | Transition task status |

## Configuration

Key environment variables (Docker profile defaults shown):

| Variable | Default | Used by |
|----------|---------|---------|
| `JWT_SECRET` | dev secret | gateway + user-service |
| `KAFKA_BOOTSTRAP` | `localhost:9092` | user/order/notification |
| `USER_SERVICE_URL`, `PRODUCT_SERVICE_URL`, `ORDER_SERVICE_URL`, `NOTIFICATION_SERVICE_URL`, `TASK_SERVICE_URL` | `http://<svc>:808x` | gateway routing |
| `RATE_LIMIT_PER_IP`, `RATE_LIMIT_PER_USER`, `RATE_LIMIT_WINDOW_SECONDS` | `100`, `1000`, `60` | gateway |
| Postgres creds | `markit`/`markit` | Docker profile |

See `ARCHITECTURE.md` for design decisions, event flows, security, and trade-offs.
