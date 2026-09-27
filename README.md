# Markit

Markit is a specialty coffee and brewing equipment storefront built as a monorepo combining a Java/Spring Boot microservices backend with a Next.js storefront.

## Overview

This repository brings together:

- a resilient backend built with Spring Boot, Spring Cloud, Kafka, and PostgreSQL
- a storefront built with Next.js, React, TypeScript, and Tailwind CSS
- a full local Docker setup for running the stack together
- supporting architecture and delivery documentation for onboarding and iteration

## Repository layout

| Path | Purpose |
|------|---------|
| `apps/storeBack/` | Java microservices backend, discovery, admin, and Gradle multi-module build |
| `apps/web/` | Next.js storefront and frontend tests |
| `docs/` | Static project assets |
| `TASKS.md` | Feature backlog, requirements, deployment notes, and roadmap |
| `00_start_here.md` | Setup and onboarding guidance |
| Root `*.md` files | Architecture, delivery summaries, and design documents |

## Backend at a glance

```text
api-gateway:8080 -> user-service:8081, product-service:8082,
                    order-service:8083, notification-service:8084, task-service:8085
                    discovery-server:8761 (Eureka), admin-server:8086 (Spring Boot Admin)
```

Key backend characteristics:

- Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.2, Gradle 9.7.1
- Saga orchestration with transactional outbox and idempotency for order integrity
- Kafka event topics for `order-events`, `user-events`, and `product-events`
- JWT authentication at the gateway with admin RBAC, rate limiting, and circuit breakers
- Caffeine caching for product reads and Feign for service-to-service communication
- Local development setup with embedded H2 and Kafka in Docker
- OpenAPI documentation exposed per service at `/swagger-ui.html` and `/v3/api-docs`
- Zipkin tracing on `localhost:9411` and Spring Boot Admin on `localhost:8086`

For implementation details and service-level documentation, see:

- `apps/storeBack/README.md`
- `apps/storeBack/ARCHITECTURE.md`

## Frontend at a glance

The frontend is a Next.js App Router storefront that defaults to a mock API and can be switched to the live backend by setting the environment variables for the API mode.

Highlights:

- Next.js 16.3.4, React 19.2.8, TypeScript, Tailwind CSS 4
- Pages for home, products, product detail, cart, checkout, orders, login, register, account, and tasks
- Auth handled through React context with localStorage session persistence
- Mock/live API client selection via a factory pattern
- Vitest + Testing Library with coverage across the UI and hooks
- Reusable UI primitives such as Button, Card, Badge, and Field

### Frontend commands

```bash
cd apps/web
npm install
npm run dev        # http://localhost:3000 (mock mode)
npm run test       # Vitest
npm run typecheck  # tsc --noEmit
```

Set the following in `.env.local` to use the live backend:

```bash
NEXT_PUBLIC_API_MODE=live
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

## Full-stack Docker startup

From the backend folder:

```bash
cd apps/storeBack
docker compose up --build
```

Runtime endpoints:

- Gateway: `http://localhost:8080`
- Web app: `http://localhost:3000` (served via nginx and HTTPS at `https://localhost`)
- Admin UI: `http://localhost:8086`
- Zipkin: `http://localhost:9411`
- Kafka: `localhost:29092`
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`

The startup order is enforced with health checks: infra → discovery → services → gateway → web → nginx.

## Quick start

### Backend

Start Kafka and launch the Java services:

```bash
cd apps/storeBack
docker compose up -d kafka
./gradlew :api-gateway:bootRun
./gradlew :user-service:bootRun
./gradlew :product-service:bootRun
./gradlew :order-service:bootRun
./gradlew :notification-service:bootRun
./gradlew :task-service:bootRun
```

### Frontend

```bash
cd apps/web
npm install && npm run dev
```

All application traffic is routed through the gateway at `http://localhost:8080`.

### Bootstrap admin credentials

- Email: `admin@markit.com`
- Password: `admin`

## Testing

| Suite | Command | Location |
|-------|---------|----------|
| Frontend unit tests | `npm run test` | `apps/web/` |
| Backend unit tests | `./gradlew test` | `apps/storeBack/` |

## Documentation map

| Document | Purpose |
|----------|---------|
| `apps/storeBack/README.md` | Backend quick start, API overview, and environment variables |
| `apps/storeBack/ARCHITECTURE.md` | Design decisions, event flow, and security architecture |
| `apps/web/README.md` | Frontend setup and local usage |
| `00_start_here.md` | Repo onboarding and build planning |
| `TASKS.md` | Delivery roadmap, product requirements, and task tracking |
| `Enhanced_requirements.md` | Product and engineering requirements |
| `Monorepo_structure.md` | Monorepo rationale |
| `Ui architecture_guide.md` | Frontend architecture guidance |

## Summary

Markit is designed as a realistic, product-oriented monorepo for learning and shipping a distributed commerce application end-to-end. It balances modern frontend UX with event-driven backend patterns and operational tooling so the project is useful both as a demo and as a strong foundation for further extension.
