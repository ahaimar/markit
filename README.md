# Markit

E-commerce monorepo — a Spring Boot microservices backend plus a Next.js storefront.

## Layout

| Path | What it is |
|------|-----------|
| `apps/storeBack/` | Backend: 6 Spring Boot services in a Gradle multi-module build (`README.md`, `ARCHITECTURE.md`) |
| `apps/web/` | Next.js storefront (watch directory separately) |
| `00_start_here.md` | Onboarding / build planning guide for this repo |
| `*.md` (root) | Requirements, architecture decisions, delivery summaries |

## Backend at a glance

```
api-gateway:8080 → user-service:8081, product-service:8082,
                  order-service:8083, notification-service:8084, task-service:8085
```

- Spring Boot 4.1.1 / Spring Cloud 2025.1.2, Java 21, Gradle 9.7.1
- Saga orchestration + transactional outbox + idempotency for order integrity
- Kafka events (`order-events`, `user-events`, `product-events`) consumed by notification-service
- JWT auth at the gateway, admin RBAC, rate limiting, circuit breakers
- Local dev uses embedded H2 + Kafka in Docker; full stack via `docker compose up --build`
- Per-service OpenAPI docs at `/swagger-ui.html` and `/v3/api-docs`

See `apps/storeBack/README.md` for the quick start, API surface, and env vars,
and `apps/storeBack/ARCHITECTURE.md` for design decisions and trade-offs.

## Frontend

`apps/web/` is the Next.js storefront. It talks to the backend through a mock API by
default; `NEXT_PUBLIC_API_MODE=live` points it at the gateway.# Markit_oo
