<p align="center"><img src="docs/icon.svg" width="64" height="64" alt="Markit logo"></p>

# Markit

E-commerce monorepo — a Spring Boot microservices backend plus a Next.js storefront for specialty coffee & brewing equipment.

## Layout

| Path | What it is |
|------|-----------|
| `apps/storeBack/` | Backend: 6 Spring Boot services + Eureka discovery + Spring Boot Admin, Gradle multi-module build (`README.md`, `ARCHITECTURE.md`) |
| `apps/web/` | Next.js 16 storefront — App Router, Tailwind CSS 4, Vitest (`README.md`) |
| `docs/` | Static assets (icon) |
| `00_start_here.md` | Onboarding / build planning guide |
| Root `*.md` | Requirements, architecture decisions, delivery summaries |

## Backend at a glance

```
api-gateway:8080 → user-service:8081, product-service:8082,
                   order-service:8083, notification-service:8084, task-service:8085
                   discovery-server:8761 (Eureka), admin-server:8086 (Spring Boot Admin)
```

- Spring Boot 4.1.1 / Spring Cloud 2025.1.2, Java 21, Gradle 9.7.1
- Saga orchestration + transactional outbox + idempotency for order integrity
- Kafka events (`order-events`, `user-events`, `product-events`) consumed by notification-service
- JWT auth at the gateway, admin RBAC, rate limiting, circuit breakers
- Caffeine caching on product reads; Feign for inter-service calls
- Local dev uses embedded H2 + Kafka in Docker; full stack via `docker compose up --build`
- Per-service OpenAPI docs at `/swagger-ui.html` and `/v3/api-docs`
- Zipkin tracing at `localhost:9411`, Admin UI at `localhost:8086`

See `apps/storeBack/README.md` for quick start, API surface, and env vars.
See `apps/storeBack/ARCHITECTURE.md` for design decisions and trade-offs.

## Frontend at a glance

`apps/web/` is the Next.js storefront (App Router). It talks to the backend through a mock API by default; set `NEXT_PUBLIC_API_MODE=live` to point it at the gateway.

- Next.js 16.3.4, React 19.2.8, TypeScript, Tailwind CSS 4
- Pages: Home, Products, Product Detail, Cart, Checkout, Orders, Order Detail, Login, Register, Account, Tasks
- Auth: React context + localStorage session persistence; mock/live API client swapped by factory
- Tests: Vitest + Testing Library (13 test files)
- Components: Navbar, Footer, ProductCard, RequireAuth, UI primitives (Button, Card, Badge, Field)

```bash
cd apps/web
npm install
npm run dev        # http://localhost:3000 (mock mode)
npm run test       # Vitest
npm run typecheck  # tsc --noEmit
```

Set `NEXT_PUBLIC_API_MODE=live` and `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` in `.env.local` for live mode.

## Full stack (Docker)

```bash
cd apps/storeBack
docker compose up --build
```

- Gateway: `http://localhost:8080`
- Web: `http://localhost:3000` (proxied via nginx on HTTPS `https://localhost`)
- Admin: `http://localhost:8086` · Zipkin: `http://localhost:9411`
- Kafka: `localhost:29092` · Postgres: `localhost:5432` · Redis: `localhost:6379`

Boot order is enforced by health checks: infra → discovery → services → gateway → web → nginx.

## Quick start (local dev)

**Backend** — start Kafka, then each service:

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

**Frontend**:

```bash
cd apps/web
npm install && npm run dev
```

All requests go through the gateway at `http://localhost:8080`. Bootstrap admin: `admin@markit.com` / `admin`.

## Testing

| Suite | Command | Location |
|-------|---------|----------|
| Frontend unit | `npm run test` | `apps/web/` (Vitest + Testing Library) |
| Backend unit | `./gradlew test` | `apps/storeBack/` (JUnit 5) |

## Documentation

| Doc | Purpose |
|-----|---------|
| `apps/storeBack/README.md` | Backend quick start, API surface, env vars |
| `apps/storeBack/ARCHITECTURE.md` | Backend design decisions, event flows, security |
| `apps/web/README.md` | Frontend getting started |
| `00_start_here.md` | Repo onboarding / build planning |
| `Enhanced_requirements.md` | Product requirements |
| `Monorepo_structure.md` | Monorepo design rationale |
| `Ui architecture_guide.md` | Frontend architecture decisions |
