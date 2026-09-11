# Build a Microservices-Based E-Commerce Backend

## Overview
Build a production-ready e-commerce microservices backend with asynchronous event processing, distributed transactions, and comprehensive observability. The system handles user authentication, product catalog management, shopping carts, order placement, and transactional notifications.

---

## Architecture

### Core Services (5 Spring Boot 3.x microservices)

Services communicate via:
- **Synchronous**: REST with OpenFeign (for internal service-to-service calls)
- **Asynchronous**: Apache Kafka (event-driven, decoupled communication)

#### 1. **api-gateway** – Request Routing & Security
**Technology**: Spring Cloud Gateway 2023.x

**Responsibilities**:
- Route all external requests to appropriate services:
  - `/api/users/**` → user-service
  - `/api/products/**` → product-service  
  - `/api/orders/**` → order-service
  - `/api/notifications/**` → notification-service
- **Rate Limiting**: 100 requests/min per IP, 1000 requests/min per authenticated user (use Spring Cloud Rate Limiter)
- **Authentication**: Extract JWT from Authorization header, validate via OpenFeign call to user-service (or embedded JWT validation)
- **Error Handling**: Return standardized error responses (400, 401, 403, 404, 429, 500+ with error codes)
- **Logging**: Structured logs (JSON) with correlation IDs (X-Correlation-ID) for distributed tracing
- **CORS**: Configurable per environment

**Endpoints**: None (pure proxy)

---

#### 2. **user-service** – Identity & Access Management
**Technology**: Spring Security 6.x, JWT, OAuth 2.0, PostgreSQL

**Features**:
- **Authentication**:
  - Email/password registration with validation (email format, password strength ≥8 chars, 1 uppercase, 1 digit)
  - Login endpoint returning `{ accessToken, refreshToken, user }`
  - JWT tokens:
    - **Access Token**: 15 minutes (claims: userId, email, roles)
    - **Refresh Token**: 7 days (stored in PostgreSQL, invalidated on logout)
  - Token refresh endpoint (validate refresh token, issue new access token)
  - Logout endpoint (revoke refresh token)

- **OAuth 2.0 Integration**:
  - Google Sign-In (authorization code flow)
  - Callback: `/auth/oauth/google/callback?code=...`
  - Auto-create user if email doesn't exist
  - Return access + refresh tokens

- **Profile Management**:
  - GET `/api/users/{userId}` – Retrieve user profile
  - PUT `/api/users/{userId}` – Update profile (email, name, address)
  - Change password endpoint
  - Delete account (soft delete)

- **Role-Based Access Control**:
  - Roles: `CUSTOMER`, `ADMIN`
  - Admins can: view all users, update user roles, deactivate accounts
  - CUSTOMER default role for new registrations

- **Database**:
  - PostgreSQL with separate schema: `user_service`
  - Tables: `users` (id, email, password_hash, name, address, role, created_at, deleted_at), `refresh_tokens`, `oauth_providers`
  - Migrations: Flyway or Liquibase

**Endpoints**:
```
POST   /api/users/register          – Register new user
POST   /api/users/login              – Login (email, password)
POST   /api/users/refresh            – Refresh access token
POST   /api/users/logout             – Logout (invalidate refresh token)
GET    /api/users/{userId}           – Get user profile
PUT    /api/users/{userId}           – Update profile
POST   /api/users/change-password    – Change password
GET    /auth/oauth/google            – Initiate Google login
GET    /auth/oauth/google/callback   – Google OAuth callback
GET    /api/users/admin/all          – [ADMIN] List all users
POST   /api/users/admin/{userId}/role – [ADMIN] Update user role
```

**Events**:
- Publish: `UserRegistered`, `UserDeleted` (Kafka topic: `user-events`)

---

#### 3. **product-service** – Catalog & Inventory
**Technology**: Spring Data JPA, PostgreSQL, Redis (Lettuce), Full-text search

**Features**:
- **CRUD Operations**:
  - GET `/api/products` – List products (paginated, filterable by category, sorted by price/name)
  - GET `/api/products/{productId}` – Get product details
  - POST `/api/products` – [ADMIN] Create product
  - PUT `/api/products/{productId}` – [ADMIN] Update product
  - DELETE `/api/products/{productId}` – [ADMIN] Soft delete product

- **Search & Filtering**:
  - POST `/api/products/search` – Full-text search (name, description) + filters:
    - `query: string`, `category: string`, `priceMin: number`, `priceMax: number`, `inStock: boolean`, `page: int`, `pageSize: int`
  - Returns: `{ items: [], total, page, pageSize }`
  - Use PostgreSQL `tsvector` or Elasticsearch (if scaling; for now, PostgreSQL LIKE is acceptable)

- **Caching Strategy**:
  - Cache product reads in Redis (Lettuce client, async)
  - **TTL**: 5 minutes
  - **Invalidation**: On update/delete, delete cache entry + publish `ProductUpdated` event
  - Cache key format: `product:{productId}`, `products:list:{page}:{category}`

- **Inventory Management** (within product-service):
  - Track `stock_quantity` field
  - Endpoint: GET `/api/products/{productId}/stock` – Get current stock
  - Endpoint: PUT `/api/products/{productId}/stock` – [ADMIN] Update stock (manual override)
  - Note: Order service will call this to decrement stock (see Resilience below)

- **Database**:
  - PostgreSQL schema: `product_service`
  - Tables: `products` (id, name, description, price, stock_quantity, category, created_at, updated_at, deleted_at)
  - Indexes on: category, created_at

**Endpoints**:
```
GET    /api/products                 – List products (paginated)
GET    /api/products/{productId}     – Get product
POST   /api/products/search          – Full-text search
POST   /api/products                 – [ADMIN] Create product
PUT    /api/products/{productId}     – [ADMIN] Update product
DELETE /api/products/{productId}     – [ADMIN] Delete product
GET    /api/products/{productId}/stock – Get stock
PUT    /api/products/{productId}/stock – [ADMIN] Update stock
```

**Events**:
- Publish: `ProductUpdated`, `ProductDeleted` (topic: `product-events`)
- Consume: (none)

---

#### 4. **order-service** – Cart & Order Lifecycle
**Technology**: Spring Data JPA, PostgreSQL, OpenFeign (call product-service), Kafka

**Features**:
- **Cart Management** (session-based, stored in database):
  - POST `/api/orders/cart/add` – Add item to cart
    - Request: `{ userId, productId, quantity }`
    - Response: `{ cartId, items: [], totalPrice }`
  - POST `/api/orders/cart/remove` – Remove item
  - GET `/api/orders/cart` – Get user's cart
  - POST `/api/orders/cart/clear` – Clear cart

- **Order Lifecycle**:
  - POST `/api/orders` – Place order
    - Request: `{ userId, cartId, shippingAddress }`
    - Validate: User exists (call user-service), cart is not empty, all products exist + in stock (call product-service)
    - **Atomic operation**: Decrement stock for each product, create order record, publish `OrderCreated` event, clear cart
    - Use `@Transactional` within order-service; for cross-service stock updates, use Saga pattern (see Resilience below)
    - Response: `{ orderId, status: "PENDING", items, totalPrice, createdAt }`
  - GET `/api/orders/{orderId}` – Get order details
  - GET `/api/orders?userId={userId}` – Get user's orders (paginated)
  - PUT `/api/orders/{orderId}/status` – [ADMIN] Update order status
    - Statuses: `PENDING` → `CONFIRMED` → `SHIPPED` → `DELIVERED` (or `CANCELLED` at any point)
    - On status change, publish `OrderStatusChanged` event

- **Resilience Patterns**:
  - **Stock Decrement**: 
    - Use OpenFeign with Resilience4j circuit breaker (3 failed attempts → open circuit, wait 30s)
    - If product-service is down: fail order creation gracefully with 503 (Service Unavailable)
    - Retry logic: 2 retries with exponential backoff (100ms, 200ms)
  - **Database Transaction Management**:
    - Use distributed saga pattern for multi-step order flow:
      1. Reserve stock (optimistic lock or explicit reserve table)
      2. Create order
      3. Publish event
      4. If step 3 fails, publish `OrderCancelled` event (compensating transaction)
  - **Idempotency**: Include `Idempotency-Key` header on POST `/api/orders`; store processed keys with result

- **Database**:
  - PostgreSQL schema: `order_service`
  - Tables:
    - `carts` (id, user_id, created_at, updated_at)
    - `cart_items` (id, cart_id, product_id, quantity)
    - `orders` (id, user_id, status, total_price, shipping_address, created_at, updated_at)
    - `order_items` (id, order_id, product_id, quantity, price_at_purchase)
    - `order_events_outbox` (id, order_id, event_type, payload, published, created_at) – for Kafka reliability
  - Indexes on: user_id, status, created_at

**Endpoints**:
```
POST   /api/orders/cart/add          – Add item to cart
POST   /api/orders/cart/remove       – Remove item from cart
GET    /api/orders/cart              – Get cart
POST   /api/orders/cart/clear        – Clear cart
POST   /api/orders                   – Place order
GET    /api/orders/{orderId}         – Get order
GET    /api/orders                   – Get user's orders
PUT    /api/orders/{orderId}/status  – [ADMIN] Update order status
```

**Events**:
- Publish: `OrderCreated`, `OrderStatusChanged`, `OrderCancelled` (topic: `order-events`)
- Consume: `ProductUpdated` (to invalidate cart if product is removed/price changed)

---

#### 5. **notification-service** – Event-Driven Notifications
**Technology**: Spring Kafka, Spring Mail (SMTP), PostgreSQL

**Features**:
- **Event Consumption**:
  - Consumer group: `notification-service`
  - Topics: `order-events`, `user-events`
  - Handle events: `UserRegistered`, `OrderCreated`, `OrderStatusChanged`

- **Email Notifications**:
  - **On OrderCreated**: Send order confirmation email with order ID, items, total price
  - **On OrderStatusChanged**: Send status update email (e.g., "Your order has been shipped!")
  - **On UserRegistered**: Send welcome email with verification link (optional)
  - SMTP Configuration: Configurable in `application.yml` (from, host, port, password)
  - Use async email sending (Spring's `@Async` or virtual threads)
  - Retry failed emails (up to 3 retries, exponential backoff)

- **SMS (Placeholder)**:
  - Log SMS intent to database (no actual SMS provider for now)
  - Table: `sms_notifications` (id, phone, message, status, created_at)
  - Future: Integrate Twilio, AWS SNS, or similar

- **Notification History**:
  - Log all notifications sent:
    - Table: `notifications` (id, event_id, recipient, type [email/sms], status [sent/failed], created_at, updated_at)
    - GET `/api/notifications?userId={userId}` – Get notification history for user (paginated)

- **Error Handling**:
  - Failed email delivery: Retry with exponential backoff, log error, don't block Kafka consumer
  - Use Dead Letter Topic: `order-events-dlq` for unprocessable messages (log + alert)

- **Database**:
  - PostgreSQL schema: `notification_service`
  - Tables: `notifications`, `sms_notifications`

**Endpoints**:
```
GET    /api/notifications            – Get notification history (paginated, filterable by status)
GET    /api/notifications/{notifId}  – Get notification details
```

**Events**:
- Consume: `UserRegistered`, `OrderCreated`, `OrderStatusChanged` (topic: `user-events`, `order-events`)
- Publish: (none, this is terminal)

---

## Tech Stack

### Languages & Runtime
- **Java 21** (LTS)
  - Virtual Threads enabled via Spring Boot 3.3+ (use `spring.threads.virtual.enabled=true`)
  - Used for async I/O-bound tasks (Kafka consumption, email sending)

### Frameworks & Libraries
- **Spring Boot 3.3.x** (latest GA, not 4.x which doesn't exist yet)
  - spring-boot-starter-web
  - spring-boot-starter-data-jpa
  - spring-boot-starter-security
  - spring-boot-starter-oauth2-client
  - spring-boot-starter-mail
  - spring-cloud-starter-gateway (api-gateway only)
  - spring-cloud-starter-openfeign (for service-to-service calls)
  - spring-kafka (async events)
  - spring-boot-starter-actuator (metrics)
  - springdoc-openapi-starter-webmvc-ui (Swagger 3.0)

### Resilience & Observability
- **Resilience4j**: Circuit breakers, retries, timeout policies
  - Dependency: `io.github.resilience4j:resilience4j-spring-boot3`
- **Micrometer**: Metrics collection
  - Prometheus exporter for scraping at `/actuator/prometheus`
- **Distributed Tracing**: Micrometer Tracing (Spring Cloud Sleuth alternative)
  - Export to Jaeger or Zipkin (optional, for advanced debugging)
- **Structured Logging**: Logback with JSON encoder (logstash-logback-encoder)

### Data & Caching
- **PostgreSQL 15+**: Primary database for all services (separate schemas per service)
- **Redis 7.x**: Caching (product-service)
  - Client: Lettuce (non-blocking, async)
  - Dependency: `spring-boot-starter-data-redis`
- **Flyway or Liquibase**: Database schema versioning & migrations

### Message Broker
- **Apache Kafka 3.5+** (Docker image: `confluentinc/cp-kafka:7.x`)
  - Topics: `user-events`, `product-events`, `order-events` (3 partitions, replication factor 1 for local dev)
  - Dead Letter Topics: `{topic}-dlq` for failed messages
  - Use Spring Kafka for producer/consumer management

### Containerization
- **Docker & Docker Compose** (v2.x)
  - Services: api-gateway, user-service, product-service, order-service, notification-service
  - Infrastructure: PostgreSQL (single instance or per-service), Redis, Kafka + Zookeeper, Prometheus (optional)
  - Docker images: Use `eclipse-temurin:21-jdk-jammy` base for services

### CI/CD
- **GitHub Actions**: Workflows for:
  - `build.yml` – Compile, test (JUnit 5 + Testcontainers), build Docker images, push to Docker Hub (optional)
  - `deploy.yml` – [Skeleton] Deploy to cloud (Kubernetes, AWS ECS, etc.) on main branch merge

### Testing
- **JUnit 5** (Jupiter) + **Mockito**: Unit tests for business logic
- **Testcontainers**: Integration tests with real PostgreSQL, Kafka, Redis
- **Spring Boot Test** annotations: `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`
- **Test Coverage**: Aim for 70%+ on core business logic
  - Run: `mvn clean test jacoco:report`

### API Documentation
- **OpenAPI 3.0** (Swagger UI):
  - Dependency: `springdoc-openapi-starter-webmvc-ui:2.x`
  - Accessible at each service's `/swagger-ui.html`
  - Auto-generated from `@OpenAPIDefinition`, `@Operation`, `@Parameter` annotations

---

## Project Structure

### Monorepo Layout
```
e-commerce-backend/
├── pom.xml                          (parent, dependency management)
├── docker-compose.yml               (full stack: services + infra)
├── .github/
│   └── workflows/
│       ├── build.yml                (CI pipeline)
│       └── deploy.yml               (CD skeleton)
├── README.md                        (architecture, setup, examples)
├── ARCHITECTURE.md                  (detailed design decisions)
├── api-gateway/
│   ├── pom.xml
│   ├── src/main/java/...
│   ├── src/test/java/...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── application-docker.yml
│   └── Dockerfile
├── user-service/
│   ├── pom.xml
│   ├── src/main/java/...
│   ├── src/test/java/...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/           (Flyway migrations)
│   │   │   └── V1__Initial_schema.sql
│   ├── Dockerfile
├── product-service/
│   ├── pom.xml
│   ├── src/main/java/...
│   ├── src/test/java/...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/
│   │   │   └── V1__Initial_schema.sql
│   ├── Dockerfile
├── order-service/
│   ├── pom.xml
│   ├── src/main/java/...
│   ├── src/test/java/...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/
│   │   │   └── V1__Initial_schema.sql
│   ├── Dockerfile
├── notification-service/
│   ├── pom.xml
│   ├── src/main/java/...
│   ├── src/test/java/...
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── db/migration/
│   │   │   └── V1__Initial_schema.sql
│   ├── Dockerfile
└── shared-lib/                      (optional: shared DTOs, utils)
    ├── pom.xml
    └── src/main/java/...
```

### Maven Structure (per service)
```
service-name/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/ecommerce/{service}/
│   │   │   ├── Application.java                   (@SpringBootApplication)
│   │   │   ├── config/                           (Spring configs: Security, Kafka, Redis, etc.)
│   │   │   ├── controller/                       (REST endpoints)
│   │   │   ├── service/                          (Business logic)
│   │   │   ├── repository/                       (Data access: Spring Data JPA)
│   │   │   ├── entity/                           (JPA entities)
│   │   │   ├── dto/                              (Data Transfer Objects)
│   │   │   ├── event/                            (Kafka events, handlers)
│   │   │   ├── exception/                        (Custom exceptions, global error handler)
│   │   │   └── util/                             (Utilities)
│   │   └── resources/
│   │       ├── application.yml                   (default config)
│   │       ├── application-docker.yml            (docker-compose config override)
│   │       ├── application-test.yml              (test config)
│   │       └── db/migration/                     (Flyway SQL migrations)
│   └── test/
│       └── java/com/ecommerce/{service}/
│           ├── controller/                       (REST endpoint tests)
│           ├── service/                          (Service/business logic tests)
│           ├── integration/                      (Integration tests with Testcontainers)
│           └── TestcontainersIntegrationTest.java
```

---

## Core Requirements

### Build & Execution
1. **Every service is independently runnable**:
   ```bash
   cd api-gateway && mvn spring-boot:run
   cd user-service && mvn spring-boot:run
   # ... etc.
   ```
   - Prerequisite: PostgreSQL, Redis, Kafka running (or mocked)
   
2. **Full stack via Docker Compose**:
   ```bash
   docker-compose up -d
   ```
   - Brings up all 5 services + PostgreSQL + Redis + Kafka + Zookeeper
   - Services should be healthy within 30 seconds
   - Use `docker-compose ps` to verify

3. **Health Checks**:
   - Each service exposes `/actuator/health` (liveness probe)
   - Docker Compose: `healthcheck` on each service with max retries 5, interval 10s

### Code Quality & Standards

1. **Dependency Injection**:
   - **Constructor injection only** (no field injection with `@Autowired`)
   - Prevents NullPointerException, enables immutability, improves testability
   - Example:
     ```java
     @Service
     public class UserService {
         private final UserRepository userRepository;
         private final PasswordEncoder passwordEncoder;
         
         public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
             this.userRepository = userRepository;
             this.passwordEncoder = passwordEncoder;
         }
     }
     ```

2. **Spring Boot Conventions**:
   - Minimal `@SpringBootApplication` class (no config bloat)
   - Use `application.yml` for external config (profiles: dev, docker, test)
   - Leverage Spring Boot starters (e.g., `spring-boot-starter-web` instead of manually adding spring-webmvc, tomcat, etc.)
   - Avoid deprecated APIs (e.g., `WebSecurityConfigurerAdapter` in Spring Security 6)

3. **API Standards**:
   - Request/Response format: JSON
   - HTTP status codes: 200 (OK), 201 (Created), 400 (Bad Request), 401 (Unauthorized), 403 (Forbidden), 404 (Not Found), 409 (Conflict), 429 (Too Many Requests), 500 (Internal Server Error)
   - Paginated responses: `{ items: [], page: 1, pageSize: 20, total: 150 }`
   - Error responses: `{ code: "ERR_001", message: "User not found", details: {...} }`
   - Timestamps: ISO 8601 UTC format (e.g., `2024-01-15T10:30:00Z`)

4. **Logging & Tracing**:
   - Structured logging (JSON format) with correlation ID
   - Log levels: INFO (important events), WARN (potential issues), ERROR (failures)
   - Avoid logging sensitive data (passwords, tokens, PII)
   - Example: `{ "timestamp": "2024-01-15T10:30:00Z", "correlationId": "abc-123", "service": "user-service", "level": "INFO", "message": "User registered" }`

5. **Exception Handling**:
   - Use custom exceptions (e.g., `UserNotFoundException`, `InsufficientStockException`)
   - Global exception handler via `@RestControllerAdvice` + `@ExceptionHandler`
   - Return standardized error DTOs

6. **Testing**:
   - **Unit Tests**: Test business logic (services) in isolation with mocks
   - **Integration Tests**: Test service + database + Kafka with Testcontainers
   - **Test Coverage**: Minimum 70% on core logic
   - Example test classes: `UserServiceTest`, `OrderServiceIntegrationTest`

---

## Detailed Requirements by Service

### API Gateway
- Rate limiting: Use Spring Cloud Rate Limiter with Redis backend (if available) or in-memory KeyResolver
- JWT validation: Either embedded (decode + verify signature) or remote call to user-service `/validate-token`
- Request logging: Log all requests/responses with correlation ID
- Fallback handling: If downstream service is down, return 503 with informative message
- CORS: Allow configurable origins (*.example.com for production)

### User Service
- Password hashing: BCrypt with strength 12
- Email validation: Regex + optional real email verification (send link to user's email)
- JWT signing: Use RS256 (RSA asymmetric) or HS256 (symmetric, simpler for this project)
- OAuth2: Store provider info to allow linking multiple providers to one user account
- Role-based endpoints: Mark endpoints with `@PreAuthorize("hasRole('ADMIN')")` and test access control

### Product Service
- Full-text search: Implement with PostgreSQL `tsvector` for simplicity; avoid Elasticsearch initially
- Caching: Use `@Cacheable` annotations with custom cache manager, manually invalidate on updates
- Stock validation: Before decrement, verify stock > 0 (use database-level check: `CHECK (stock_quantity >= 0)`)
- Pagination: Implement `Pageable` interface via Spring Data JPA

### Order Service
- Saga pattern: Implement compensating transactions for order cancellation
- Outbox pattern: Write order + event to database in single transaction; separate Kafka publisher polls outbox
- Stock reservation: Use optimistic locking (`@Version` field) or explicit reserve table
- Idempotency: Store `Idempotency-Key` + request hash in database; return cached result if duplicate request

### Notification Service
- Async email: Use `@Async` with `@EnableAsync` or virtual threads
- Email templates: Use Thymeleaf for HTML email templates
- Retry strategy: Exponential backoff (1s, 2s, 4s) up to 3 retries
- Dead letter queue: Redirect unprocessable events to `order-events-dlq` after max retries

---

## Integration Test Strategy

### Test Scenarios

1. **Order Flow End-to-End**:
   ```
   User registers → User logs in → Browse products → Add to cart → Place order 
   → Order created in DB → OrderCreated event published to Kafka 
   → Notification service consumes event → Email sent (logged)
   ```
   - **Test Class**: `OrderServiceIntegrationTest`
   - **Setup**: Testcontainers (PostgreSQL + Kafka)
   - **Assertions**: Order status, Kafka message presence, notification logged

2. **Stock Decrement Safety**:
   - Place two concurrent orders for same product with limited stock
   - Verify only one succeeds, other fails with "Insufficient stock"
   - **Test Class**: `ConcurrentOrderTest`

3. **API Gateway Rate Limiting**:
   - Send 101 requests in 1 minute from same IP
   - Verify 101st request returns 429 (Too Many Requests)
   - **Test Class**: `ApiGatewayRateLimitTest`

4. **User Authentication**:
   - Register user → Attempt login with wrong password → Verify 401 returned
   - Login with correct credentials → Verify access token returned
   - Use access token in subsequent requests → Verify 200 OK
   - Refresh token after access token expires → Verify new token issued
   - **Test Class**: `UserAuthenticationTest`

---

## Deliverables Checklist

- [ ] Complete Maven project structure with parent pom.xml + 5 service pom.xmls
- [ ] Application classes (`@SpringBootApplication`) for each service
- [ ] REST controllers with full CRUD endpoints (documented with `@Operation`)
- [ ] Service layer (business logic)
- [ ] Spring Data JPA entities and repositories
- [ ] Kafka producer/consumer configurations
- [ ] Security config (JWT, OAuth2, RBAC)
- [ ] Redis cache config (if used)
- [ ] Database migration scripts (Flyway V1__ files)
- [ ] Global exception handler with standardized error responses
- [ ] Dockerfile for each service
- [ ] docker-compose.yml (all services + PostgreSQL + Redis + Kafka)
- [ ] GitHub Actions workflows (build.yml, deploy.yml skeleton)
- [ ] application.yml + application-docker.yml for each service
- [ ] Integration tests using Testcontainers (≥3 test scenarios)
- [ ] Unit tests with mocks (service layer)
- [ ] Swagger/OpenAPI documentation (`@OpenAPIDefinition`, endpoint descriptions)
- [ ] README.md with:
  - [ ] System architecture diagram (Mermaid or ASCII)
  - [ ] Setup instructions (local + Docker Compose)
  - [ ] API documentation (curl examples for key endpoints)
  - [ ] Design decisions & trade-offs (distributed transactions, caching strategy, etc.)
  - [ ] Future improvements (Elasticsearch, Kubernetes, Istio, etc.)
- [ ] ARCHITECTURE.md with:
  - [ ] Data flow diagrams
  - [ ] Event-driven design rationale
  - [ ] Error handling strategy
  - [ ] Security considerations
  - [ ] Scalability & performance notes

---

## Key Design Decisions

### 1. **Synchronous vs. Asynchronous Communication**
- **Synchronous (REST)**: User → API Gateway → services (direct calls, immediate response)
- **Asynchronous (Kafka)**: Events between services (decoupled, scalable, resilient)
- **Trade-off**: Async adds complexity (eventual consistency) but improves resilience

### 2. **Database Per Service Pattern**
- Each service has its own PostgreSQL schema (logical separation)
- Pro: Independent scaling, loose coupling
- Con: Distributed queries require service-to-service calls; joins impossible
- Alternative: Consider shared database for simpler projects, migrate to separate DBs later

### 3. **Saga Pattern for Distributed Transactions**
- Order placement touches multiple services (validate stock, decrement inventory, create order, send notification)
- Use orchestration (order-service) or choreography (event-driven)
- Implement compensating transactions (e.g., if email fails, don't fail order, just retry async)

### 4. **Caching Strategy**
- Cache products (read-heavy, infrequent updates)
- Don't cache user/order data (write-heavy, freshness critical)
- Invalidate on explicit update, not just TTL expiry

### 5. **Virtual Threads over Traditional Thread Pools**
- Java 21+ feature: lightweight, scalable for I/O-bound tasks
- Use for Kafka consumption, email sending, external API calls
- Reduces context-switch overhead vs. traditional async/await

---

## Future Enhancements

1. **Search**: Replace PostgreSQL LIKE with Elasticsearch or OpenSearch (better full-text search, aggregations)
2. **Service Mesh**: Deploy on Kubernetes with Istio/Linkerd (automatic retries, circuit breaking, mTLS)
3. **Distributed Tracing**: Integrate with Jaeger for request tracing across services
4. **Real SMS**: Integrate Twilio or AWS SNS for SMS notifications
5. **Caching Layers**: Add L1 cache (in-process) + L2 (Redis) for product queries
6. **Event Sourcing**: Store all order state changes as immutable events (replay, audit trail)
7. **Load Testing**: Use JMeter/Gatling to simulate high traffic and identify bottlenecks
8. **API Versioning**: Implement `/api/v1/`, `/api/v2/` for backward compatibility

