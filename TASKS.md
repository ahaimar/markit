# Markit — Tasks, Features & Requirements

Consolidated task and feature documentation for the Markit e-commerce platform.

---

## Table of Contents

1. [New Features (12)](#1-new-features-12)
2. [Frontend Tasks](#2-frontend-tasks)
3. [API Rate Limits & SLA](#3-api-rate-limits--sla)
4. [Testing Strategy](#4-testing-strategy)
5. [Deployment & DevOps](#5-deployment--devops)
6. [Security Enhancements](#6-security-enhancements)
7. [Performance Optimization](#7-performance-optimization)
8. [Scalability Roadmap](#8-scalability-roadmap)
9. [Critical Corrections Made](#9-critical-corrections-made)
10. [Improvements Applied](#10-improvements-applied)
11. [Deliverables Status](#11-deliverables-status)
12. [Quick Start Commands](#12-quick-start-commands)

---

## 1. New Features (12)

### 1.1 Reviews & Ratings System
- **Endpoints**:
  - `POST /api/products/{productId}/reviews` — Customer submits review (1-5 stars, text, photos)
  - `GET /api/products/{productId}/reviews` — Paginated reviews list
  - `PUT /api/products/{productId}/reviews/{reviewId}` — Edit own review
  - `DELETE /api/products/{productId}/reviews/{reviewId}` — Delete own review
  - `POST /api/products/{productId}/reviews/{reviewId}/helpful` — Mark review as helpful
- **Database**: `reviews` table (id, product_id, user_id, rating, title, body, image_urls, helpful_count, created_at); triggers recalculate `product.avg_rating`
- **Kafka Events**: `ReviewCreated`, `ReviewDeleted` → Notification service sends email to product seller
- **Cache**: Product detail includes rating; invalidate when review added/deleted

### 1.2 Wishlist / Favorites
- **Endpoints**:
  - `POST /api/wishlists/items` — Add product to wishlist
  - `DELETE /api/wishlists/items/{productId}` — Remove from wishlist
  - `GET /api/wishlists` — Get user's wishlist (paginated)
  - `POST /api/wishlists/share` — Share wishlist link (public/private)
- **Database**: `wishlists` (id, user_id, name, is_public, created_at), `wishlist_items` (id, wishlist_id, product_id, added_at)
- **Features**: Track "Add to Wishlist" counts; sale notifications; export wishlist as PDF

### 1.3 Payment Processing (Stripe Integration)
- **New Service: `payment-service`** (or extend order-service)
- **Endpoints**:
  - `POST /api/payments/intent` — Create payment intent
  - `POST /api/payments/confirm` — Confirm payment
  - `POST /api/payments/refund/{paymentId}` — Initiate refund
  - `POST /api/payments/webhook` — Stripe webhook handler
- **Workflow**: Order placed (PENDING) → Create Stripe intent → Frontend collects card → Confirm → Order CONFIRMED → Webhook updates to PAID → Publish `PaymentSucceeded` → Receipt email
- **Security**: Stripe API keys from env; PCI compliance (never handle raw card data); webhook signature verification

### 1.4 Admin Analytics Dashboard
- **Endpoints** (admin only):
  - `GET /api/analytics/dashboard` — Summary: total revenue, orders, users, top products
  - `GET /api/analytics/revenue` — Revenue trend (daily/weekly/monthly)
  - `GET /api/analytics/products` — Top/bottom performing products
  - `GET /api/analytics/users` — User growth, retention, LTV
  - `GET /api/analytics/search` — Search trends, popular queries
  - `GET /api/analytics/export` — Export reports as CSV/PDF
- **Database**: Optional separate analytics schema with aggregated tables; or real-time queries with pre-computed aggregates
- **React Admin**: Revenue chart (Recharts), top 10 products table, user growth bar chart, search heatmap

### 1.5 Returns & Refunds
- **Endpoints**:
  - `POST /api/orders/{orderId}/returns` — Create return request
  - `GET /api/orders/{orderId}/returns` — Get return status
  - `PUT /api/orders/{orderId}/returns` — [ADMIN] Approve/reject return
- **Workflow**: Customer initiates → RETURN_INITIATED → Admin reviews → RETURN_APPROVED → Email shipping label → Customer ships → RETURN_RECEIVED → Calculate refund → Stripe refund → REFUNDED → Confirmation email
- **Database**: `returns` table (id, order_id, reason, status, requested_at, approved_at, refund_id)

### 1.6 Inventory Management (Real-Time)
- **Enhanced Endpoints**:
  - `PUT /api/products/{productId}/stock` — [ADMIN] Adjust stock (with reason)
  - `GET /api/products/low-stock` — [ADMIN] Products below threshold (< 10 units)
  - `POST /api/products/stock-alerts` — [ADMIN] Configure low stock alerts
- **Real-Time Updates**: WebSocket to notify admins; dashboard widget; Kafka `StockLow` event → alert email
- **Historical Tracking**: `stock_history` table (id, product_id, quantity_before, quantity_after, reason, admin_id, created_at)

### 1.7 Search & Recommendations
- **Enhanced Search**: Typo correction, suggestions, search history tracking
- **Product Recommendations** (ML-ready):
  - `GET /api/products/recommendations?userId={id}` — Recommended products
  - Algorithm: Collaborative filtering; store user interactions; Kafka → ML pipeline
- **Trending Products**: `GET /api/products/trending` — Top products (views + sales last 7 days)

### 1.8 Email Marketing & Notifications
- **Enhanced Notification Service**: Consume `UserRegistered`, `OrderCreated`, `OrderStatusChanged`, `ReviewCreated`, `WishlistItemSaleAlerted`
- **Email Types**: Welcome, order confirmation + receipt, shipment notification, delivery confirmation, review request, wishlist sale, weekly newsletter, returns/refund confirmation
- **Features**: Thymeleaf templates, `marketing-events` Kafka topic, unsubscribe handling

### 1.9 User Preferences & Settings
- **Endpoints**:
  - `GET/PUT /api/users/{userId}/preferences` — Notification settings, language, timezone
  - `GET/POST/DELETE /api/users/{userId}/addresses` — Multiple shipping addresses
- **Features**: Default address, saved payment methods, email opt-in/out, timezone for order display

### 1.10 Admin Role Management
- **Role Hierarchy**: CUSTOMER → VENDOR → MODERATOR → ADMIN → SUPER_ADMIN
- **Endpoints**:
  - `GET /api/admin/users` — [ADMIN] List users with roles
  - `PUT /api/admin/users/{userId}/role` — [ADMIN] Assign role
  - `DELETE /api/admin/users/{userId}` — [SUPER_ADMIN] Delete user
- **Database**: `role` field on users; `role_permissions` table (role, resource, action)

### 1.11 Coupons & Promotions
- **Endpoints**:
  - `POST /api/coupons/validate` — Validate coupon code
  - `GET /api/coupons` — List active coupons
  - `POST /api/orders/{orderId}/apply-coupon` — Apply coupon to order
- **Coupon Types**: Percentage discount, fixed amount, free shipping, BOGO
- **Constraints**: Min order amount, expiration, usage limit (global + per-user), applicable categories
- **Database**: `coupons` (code, discount_type, discount_value, min_amount, expiry, usage_limit), `coupon_usage`

### 1.12 Support/Help System
- **Endpoints**:
  - `POST /api/tickets` — Create support ticket
  - `GET /api/tickets/{ticketId}` — Get ticket details + conversation
  - `POST /api/tickets/{ticketId}/reply` — Add message
  - `GET /api/faqs` — Frequently asked questions
  - `GET /api/help/search` — Search help articles
- **Features**: Live chat integration, ticket status (OPEN, IN_PROGRESS, RESOLVED, CLOSED), SLA tracking

---

## 2. Frontend Tasks

### 2.1 Next.js Storefront — New Pages
| Page | Purpose |
|------|---------|
| `/` | Homepage with hero, featured products, trending |
| `/products` | Product listing with filters, search, sorting |
| `/products/[id]` | Product detail (images, reviews, related products) |
| `/cart` | Shopping cart |
| `/checkout` | Multi-step checkout (shipping, payment, review) |
| `/checkout/success` | Order confirmation |
| `/account/orders` | Order history |
| `/account/orders/[id]` | Order detail + tracking |
| `/account/wishlist` | Saved items |
| `/account/settings` | Preferences, addresses, payment methods |
| `/account/reviews` | My reviews |
| `/search` | Search results with filters |
| `/category/[slug]` | Category browse |

### 2.2 React Admin Dashboard — New Pages
| Page | Purpose |
|------|---------|
| `/dashboard` | Overview (revenue, orders, users, KPIs) |
| `/analytics` | Charts (revenue trend, top products, user growth) |
| `/orders` | Order list (filter by status, search) |
| `/orders/[id]` | Order detail + manage returns |
| `/products` | Product inventory (stock levels, edit) |
| `/products/new` | Create product |
| `/products/[id]/edit` | Edit product + upload images |
| `/products/[id]/reviews` | Reviews moderation |
| `/users` | User management + role assignment |
| `/coupons` | Create/manage coupons |
| `/reports` | Export reports (CSV, PDF) |
| `/settings` | System config (SMTP, payment keys) |
| `/support/tickets` | Support tickets + live chat |
| `/notifications` | Email templates, campaign history |

---

## 3. API Rate Limits & SLA

### Rate Limits (at API Gateway)
| Endpoint Type | Limit |
|---------------|-------|
| Public | 60 req/min per IP |
| Authenticated | 600 req/min per user |
| Admin | 1000 req/min per admin |

### SLA
| Metric | Target |
|--------|--------|
| API response time | < 200ms (p95) |
| Product search | < 500ms |
| Page load | < 1s (after first byte) |
| Uptime | 99.9% |

---

## 4. Testing Strategy

### Unit Tests (70%+ coverage)
- Services, repositories, DTOs
- Validation logic (email, password, coupon)

### Integration Tests (Testcontainers)
- Full order flow: add to cart → place order → payment → notification
- Search with filters
- Coupon validation
- Return flow: initiate → approve → refund → notification

### API Contract Tests (Pact)
- Frontend ↔ Backend API contracts
- Ensures frontend doesn't break when backend changes

### E2E Tests (Playwright)
- User registration → browse → wishlist → checkout → order confirmed
- Admin: login → create coupon → view analytics → create support ticket
- Search: type query → see results → click product → read review → add to cart

### Load Testing (JMeter/Gatling)
- 100 concurrent users → checkout flow
- 50 concurrent users → search with filters
- Identify bottlenecks (cache misses, DB slow queries)

### Performance Testing
- Lighthouse score target: 90+ (mobile, desktop)
- Core Web Vitals: LCP < 2.5s, FID < 100ms, CLS < 0.1

---

## 5. Deployment & DevOps

### CI/CD Pipeline (GitHub Actions)
- **Build**: Compile, unit tests, SonarQube code quality
- **Integration**: Testcontainers tests, API contract tests
- **Security**: OWASP dependency check, SonarQube security rules
- **Build artifacts**: Docker images for each service
- **Deploy**: Staging (auto), Production (manual approval)

### Monitoring (Observability)
- **Metrics**: Prometheus scrape each service at `/actuator/prometheus`
- **Dashboards**: Grafana with predefined dashboards (requests, errors, latency, JVM heap)
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana) with structured JSON logs
- **Tracing**: Jaeger for distributed tracing (correlation IDs)
- **Alerts**: PagerDuty (high error rate, slow response time, service down)

### Environments
| Environment | Purpose |
|-------------|---------|
| Local | Docker Compose (dev machine) |
| Staging | Kubernetes cluster on AWS EKS (production-like) |
| Production | AWS ECS or EKS with auto-scaling, CDN, database backups |

---

## 6. Security Enhancements

### Authentication & Authorization
- OAuth2 + JWT (already in spec)
- MFA (optional, for admin users)
- Password reset (email verification)
- Session management (token expiry, logout, revoke)

### Data Protection
- HTTPS everywhere (TLS 1.3)
- HSTS (Strict-Transport-Security)
- Database encryption at rest (AWS KMS)
- PII encryption (user addresses, payment methods)

### API Security
- CORS configured (only trusted origins)
- Rate limiting (DDoS protection)
- Input validation (SQL injection, XSS prevention)
- Output encoding
- CSRF protection (if using cookies)

### Code Security
- Dependency scanning (vulnerable packages)
- SAST (Static Application Security Testing)
- DAST (Dynamic Application Security Testing)
- Regular security audits

---

## 7. Performance Optimization

### Backend
- Database indexing (product.category, order.created_at, user.email)
- Query optimization (N+1 detection, lazy loading)
- Caching (Redis): products, categories, trending items
- Async processing (Kafka): notifications, analytics
- CDN for static assets (images, CSS, JS)

### Frontend
- Image optimization (Next.js Image component)
- Code splitting (lazy load pages)
- Service workers (offline support)
- Gzip compression
- Browser caching headers

---

## 8. Scalability Roadmap

### Phase 1 — MVP (month 1-2)
- Single database per service
- Redis single instance
- Kafka single broker
- Deploy on AWS ECS

### Phase 2 — Scale (month 3-4)
- Database read replicas (write to primary, read from replicas)
- Redis cluster (high availability)
- Kafka cluster (3 brokers, replication factor 3)
- Load balancer (distribute traffic)

### Phase 3 — Enterprise (month 5+)
- Kubernetes (EKS) for orchestration
- Service mesh (Istio) for observability
- Multi-region deployment (AWS regions)
- Event sourcing for order audit trail
- Elasticsearch for product search (scale beyond PostgreSQL LIKE)

---

## 9. Critical Corrections Made

| Original Issue | Correction | Impact |
|---------------|------------|--------|
| "Spring Boot 4 services" (doesn't exist) | Changed to Spring Boot 3.3.x with clear Spring Cloud versions | Prevents implementation of non-existent framework |
| "Java 21 (Virtual Threads enabled)" — unclear | Specified opt-in via `spring.threads.virtual.enabled=true`, for I/O-bound tasks | Developers know exactly where to apply |
| No mention of distributed transactions | Added Saga pattern, compensating transactions, Outbox pattern | Prevents subtle data consistency bugs in order flow |

---

## 10. Improvements Applied

### Resilience Patterns
- Circuit breakers (Resilience4j), retry logic, timeouts
- Example: Order service → Product service stock check with max 2 retries, 30s circuit break window

### Distributed Tracing & Correlation IDs
- `X-Correlation-ID` header throughout all services, structured JSON logging

### Idempotency
- `Idempotency-Key` header on order creation, duplicate detection via database

### Database Migrations
- Flyway/Liquibase configuration per service with `V1__` migration files

### API Standards
- Detailed pagination format, error response structure, timestamp formats (ISO 8601)
- Example: `{ items: [], page: 1, pageSize: 20, total: 150 }`

### Dead Letter Queues
- Handle unprocessable Kafka messages via `{topic}-dlq`

### Health Checks
- `/actuator/health` on every service, Docker healthchecks with max 5 retries

### Test Coverage Strategy
- Specific test scenarios (concurrent orders, rate limiting, auth flow)
- Testcontainers setup with PostgreSQL + Kafka

### Expanded Service Specifications
- **API Gateway**: Fallback handling, CORS config, request logging, rate limiting backend choice
- **User Service**: Password strength, email validation, JWT key type, token refresh flow, OAuth2 linking, role assignment
- **Product Service**: Full-text search guidance, stock constraints, cache key format, pagination
- **Order Service**: Stock reservation strategy, Saga pattern, Outbox pattern, idempotency, compensating transactions
- **Notification Service**: Async email strategy, Thymeleaf templates, retry backoff, DLQ handling

### Best Practices Added
- Constructor injection, deprecation warnings, structured logging, sensitive data rules
- HTTP status code specificity (429 for rate limiting), error DTO format, ISO 8601 timestamps
- BCrypt password hashing, JWT signing algorithm guidance, RBAC with `@PreAuthorize`
- Health checks, correlation IDs, Prometheus metrics, startup verification

---

## 11. Deliverables Status

### Complete (Ready to Use)
- [x] Backend Maven/Gradle setup (parent build.gradle)
- [x] User Service (full implementation)
- [x] Docker Compose infrastructure
- [x] GitHub Actions CI/CD pipeline
- [x] Next.js storefront foundation
- [x] Authentication flows (JWT + OAuth2)
- [x] API client setup (Axios + interceptors)
- [x] Shopping cart (Zustand)
- [x] RESTful API patterns
- [x] Project structure & documentation

### TODO (Follow the Pattern)
- [ ] Product Service (use User Service as template)
- [ ] Order Service (add saga orchestration)
- [ ] Notification Service (Kafka consumer setup)
- [ ] API Gateway (Spring Cloud routing)
- [ ] Database migrations (DDL scripts)
- [ ] React admin dashboard (Redux + data tables)
- [ ] Product pages (detail, search, reviews)
- [ ] Checkout flow (payment, address form)
- [ ] Additional features (reviews, wishlist, coupons)
- [ ] Integration tests (Testcontainers)

---

## 12. Quick Start Commands

```bash
# Start entire stack
docker-compose up -d

# Access applications
open http://localhost:3000        # Storefront
open http://localhost:3001        # Admin
open http://localhost:8080        # API Gateway
open http://localhost:8025        # MailHog (test emails)

# Check service health
curl http://localhost:8081/actuator/health  # User Service
curl http://localhost:8082/actuator/health  # Product Service

# View logs
docker-compose logs -f api-gateway
docker-compose logs -f notification-service

# Run tests
cd backend && mvn test
cd frontend/storefront && npm test

# Stop services
docker-compose down
```
