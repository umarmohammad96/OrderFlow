# OrderFlow — Implementation Plan

Source: *OrderFlow Implementation Blueprint* (sections 1–14). This plan maps every blueprint
requirement to a phase, a concrete design decision, and the test that proves it.

## Technology decisions

| Layer | Choice | Why |
|---|---|---|
| Backend | Java 25 (LTS), Spring Boot 3.5, Spring Security, Spring Data JPA | Blueprint stack; Java 25 LTS runtime |
| Auth | Spring Security OAuth2 Resource Server (Nimbus) with HS256 JWT | Framework-native JWT validation, no hand-rolled filter |
| DB | PostgreSQL 16 + Flyway migrations | Versioned, reviewable schema; `ddl-auto=validate` |
| Cache | Redis via Spring Cache, typed JSON serializers, TTL | Blueprint 8.3 |
| Messaging | RabbitMQ topic exchange + DLQ, **transactional outbox** relay | Blueprint 8.4 without losing events when the broker is down |
| Tests | JUnit 5, Mockito, Spring Boot Test, Testcontainers (real Postgres/Redis/RabbitMQ) | Blueprint 10 — integration tests run against the real engines |
| API testing | Postman collection, executed with Newman against the compose stack | Blueprint 6/13 |
| Frontend | React (Vite), JavaScript, Axios, React Router | Blueprint stack |
| Infra | Multi-stage Dockerfiles, Docker Compose (app, postgres, redis, rabbitmq, web) | Blueprint 11 |

## Key design decisions

1. **Concurrency-safe inventory (8.1)** — conditional atomic `UPDATE … WHERE available_quantity >= :qty`;
   affected-rows = 0 ⇒ `409 OUT_OF_STOCK` and the whole order transaction rolls back. Multi-item orders
   reserve in product-id order to avoid deadlocks. `version` column kept for optimistic locking on admin restock.
2. **Idempotent order creation (8.2)** — `Idempotency-Key` header required. Key row is inserted
   (`ON CONFLICT DO NOTHING`) *inside* the order transaction, so a concurrent duplicate blocks on the unique
   index and then replays the committed snapshot. Same key + different payload ⇒ `422 IDEMPOTENCY_KEY_REUSED`.
   Failed attempts roll back the key, so a retry after e.g. out-of-stock is re-evaluated. Keys expire after 24h.
3. **Caching (8.3)** — product details and product list pages cached in Redis (TTL 10 min); any product write
   evicts the detail entry and all list pages. Stock is *not* cached — it is merged live from inventory so
   caching can never cause overselling or stale stock display.
4. **Async workflow (8.4)** — payment success writes `OrderConfirmedEvent` to an `outbox_events` table in the
   same transaction as the status change. A scheduled relay publishes to RabbitMQ (`SKIP LOCKED`, publisher
   confirms). The notification consumer is idempotent (event-id dedupe) and has a DLQ. The HTTP request never
   waits for the consumer.
5. **Transactions (8.5)** — order creation (key + reservation + order + cart cleanup) is one transaction.
   Payment is split: tx1 (lock order, mark `PAYMENT_PENDING`, insert `PENDING` payment) → mock gateway call
   **outside** any transaction → tx2 (lock order, record result, confirm + commit reservation + outbox).
6. **State machine (7)** — `OrderStatus` enum owns the allowed-transition table; every change goes through it.
   `CREATED|PAYMENT_PENDING → CANCELLED` releases reserved stock. `DELIVERED` cannot be cancelled.
7. **Common** — `ApiResponse` envelope, `GlobalExceptionHandler`, Bean Validation, `X-Request-Id` correlation
   filter (MDC + response header + propagated into RabbitMQ message headers).

## Phases (blueprint §12 sequence)

| # | Phase | Deliverable | Verified by |
|---|---|---|---|
| 1 | Scaffold | Maven project, package structure (§9), common module, Flyway V1 schema (§5) | build compiles, context loads |
| 2 | Users & Auth | User entity, register/login, BCrypt, JWT, roles, 401/403 handlers | AuthService unit + auth API tests |
| 3 | Products | Category/Product CRUD (admin), validation, pagination, filtering | Product API tests |
| 4 | Inventory & Cart | Atomic reserve/release/commit, restock, cart CRUD | repository + cart API tests |
| 5 | Orders | Order + items, pricing, state machine, cancel, admin status updates | state-machine + pricing unit tests, order API tests |
| 6 | Concurrency + transactions | Reservation inside order tx, deadlock-safe ordering | concurrent last-unit test |
| 7 | Idempotency | Idempotency-Key handling, replay, mismatch, purge | sequential + concurrent idempotency tests |
| 8 | Redis caching | Cache config, TTL, eviction, statistics | cache hit/evict test |
| 9 | Payments + RabbitMQ | Mock gateway, retry-safe payment, outbox relay, consumer, DLQ | payment tests, consumer test |
| 10 | Test suite | Unit (Mockito) + integration (Testcontainers) per §10 | `mvn verify` green |
| 11 | Frontend | React app: auth, catalog, cart, checkout, orders, payment, admin | `npm run build`, browser walkthrough |
| 12 | Docker | Dockerfiles, compose with healthchecks, `.env.example` | `docker compose up` healthy |
| 13 | Docs & evidence | README (architecture, request flow, ER, cache/event flow), Postman collection, screenshots, limitations | Newman run green against compose |

Final step: requirement-by-requirement audit of the blueprint against the repository (`docs/BLUEPRINT_AUDIT.md`).
