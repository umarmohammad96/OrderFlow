# Blueprint audit

This checks each requirement in *OrderFlow Implementation Blueprint* against the repository.

Legend:
- ✅ done and verified (test or live run)
- ⚠️ partially done, with the reason
- ❌ not done

## §1 Project goal
| Requirement | Status | Evidence |
|---|---|---|
| Transactional workflows, concurrency control, idempotency, caching, async processing, auth, testing, deployment | ✅ | Sections below |
| Modular Spring Boot app first, service-ready, not prematurely split | ✅ | Single deployable; modules reference each other by ID; decomposition plan in README |

## §2 Stack
| Layer | Status | Implementation |
|---|---|---|
| React.js, JavaScript, Axios, React Router | ✅ | `frontend/` (React 18, Vite, no TypeScript) |
| Java, Spring Boot, Spring Security, Spring Data JPA | ✅ | Java 21, Boot 3.5.16 |
| PostgreSQL | ✅ | 16, Flyway migrations |
| Redis | ✅ | 7, Spring Cache |
| RabbitMQ | ✅ | 3.13, topic exchange + DLQ |
| JUnit 5, Mockito, Spring Boot Test | ✅ | 140 tests (+ Testcontainers) |
| Postman | ✅ | `postman/` collection; 55 requests / 158 assertions pass with Newman |
| Docker, Docker Compose, Git/GitHub | ⚠️ | Docker + Compose ✅, local Git repo ✅. **Not pushed to GitHub**: that needs your account and approval |

## §3 Architecture
| Requirement | Status | Evidence |
|---|---|---|
| React client → API layer → Auth/Product/Inventory/Cart/Order/Payment modules + Notification worker | ✅ | README architecture diagram |
| Product ↔ Redis, Inventory ↔ PostgreSQL, Order → RabbitMQ | ✅ | The order-confirmed event is produced in the payment step (transactional outbox) |
| Controller → Service → Repository → DB; DTO/Mapper/Validation/Exception handler; Security/transaction boundaries | ✅ | Every module |

## §4 Core modules
| Module | Status | Evidence |
|---|---|---|
| Auth: registration, login, JWT, password hashing, role checks | ✅ | `AuthApiIT`, `AuthServiceTest` |
| Products: create/update/list/get, category and price management | ✅ | `ProductApiIT` (also deactivate, search, filter, sort) |
| Inventory: available/reserved quantities, concurrency-safe reservation | ✅ | `ConcurrentInventoryIT`, `InventoryRepositoryIT` |
| Cart: add/remove/update, retrieve | ✅ | `CartApiIT` |
| Orders: creation, items, total calculation, status transitions, cancellation | ✅ | `OrderApiIT`, `OrderStatusTest`, `PricingServiceTest` |
| Payments: mock authorisation, success/failure, retry-safe | ✅ | `PaymentAndNotificationIT`, `PaymentServiceTest` |
| Notifications: publish order events to RabbitMQ, process asynchronously | ✅ | `PaymentAndNotificationIT` |
| Common: API response model, validation, global exception handling, logging, correlation/request IDs | ✅ | `ApiResponse`, `GlobalExceptionHandler`, `RequestIdFilter` (propagated into RabbitMQ) |

## §5 Database design
| Table | Status | Notes |
|---|---|---|
| users (id, name, email, password_hash, role, created_at) | ✅ | Case-insensitive unique email |
| products (id, name, description, price, category_id, active, created_at) | ✅ | + `updated_at`; `categories` table backs `category_id` |
| inventory (product_id, available_quantity, reserved_quantity, version) | ✅ | CHECK ≥ 0 constraints |
| carts (id, user_id, created_at, updated_at) | ✅ | |
| cart_items (id, cart_id, product_id, quantity) | ✅ | Unique (cart_id, product_id) |
| orders (id, user_id, total_amount, status, created_at, updated_at) | ✅ | + `version` |
| order_items (id, order_id, product_id, quantity, unit_price) | ✅ | + `product_name` snapshot |
| payments (id, order_id, amount, status, transaction_reference, created_at) | ✅ | + `failure_reason`, `updated_at`; partial unique indexes |
| idempotency_keys (id, user_id, request_key, request_hash, response_snapshot, created_at) | ✅ | Unique (user_id, request_key); JSONB snapshot |
| Additional tables | ✅ | `categories`, `order_status_history`, `outbox_events`, `notifications` |

## §6 REST API contract (all 14 endpoints)
| Endpoint | Status |
|---|---|
| POST /api/auth/register · POST /api/auth/login | ✅ |
| GET /api/products (paginated) · GET /api/products/{id} · POST /api/products (admin) | ✅ |
| POST /api/cart/items · GET /api/cart · PATCH /api/cart/items/{id} · DELETE /api/cart/items/{id} | ✅ |
| POST /api/orders (requires Idempotency-Key) · GET /api/orders/{id} · GET /api/orders · POST /api/orders/{id}/cancel | ✅ |
| POST /api/payments/{orderId} | ✅ |
| Additional endpoints | ✅ |

The additional endpoints are: `GET /api/auth/me`, categories, `PUT`/`DELETE` products, inventory admin,
`GET /api/payments/{orderId}`, `/api/notifications` and `/api/admin/orders`.

## §7 Order state machine
| Requirement | Status | Evidence |
|---|---|---|
| CREATED → PAYMENT_PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED; CREATED/PAYMENT_PENDING → CANCELLED | ✅ | `OrderStatus.allowedNext()`; 28 parameterised tests |
| Only explicitly permitted transitions succeed | ✅ | 409 `INVALID_STATE_TRANSITION` (`OrderApiIT`, Postman) |
| Delivered order cannot be cancelled through the normal API | ✅ | `fullLifecycleAndDeliveredOrderCannotBeCancelled`, Postman |

## §8 Critical engineering implementations
| Requirement | Status | Evidence |
|---|---|---|
| 8.1 Transaction + conditional atomic update; check affected rows; 0 → conflict | ✅ | `InventoryRepository.reserve` (blueprint SQL + version bump); `OutOfStockException` |
| 8.1 Integration test firing concurrent order attempts | ✅ | `ConcurrentInventoryIT`: 20 buyers/1 unit → 1 success; 30/10 → 10; no deadlocks; live demo 25 buyers → 1 × 201, 24 × 409 |
| 8.2 Require Idempotency-Key; persist key + user + request hash + response; retry returns original | ✅ | `IdempotencyIT` (incl. 15 concurrent duplicates → 1 order) |
| 8.3 Cache product details/lists; TTL; invalidate after changes; measure cache hits | ✅ | `ProductCacheIT` (1 miss / 9 hits, TTL, eviction); Actuator `cache.gets` metrics |
| 8.4 Order confirmed → OrderConfirmedEvent → RabbitMQ → consumer → mock email/SMS; request does not wait | ✅ | `PaymentAndNotificationIT.orderConfirmationDoesNotWaitForTheNotificationConsumer` |
| 8.5 Transaction boundaries around order creation, reservation, payment/order updates; external work outside | ✅ | One tx for order creation; payment split into tx1 / gateway call / tx2; `Propagation.MANDATORY` on helpers |

## §9 Package structure
| Requirement | Status |
|---|---|
| `com.orderflow.{auth{controller,service,dto,security},product,inventory,cart,order,payment,notification,common{exception,response,config}}`, `OrderFlowApplication.java` | ✅ |
| Each module: controller → service → repository → domain/entity → dto/mapper | ✅ |

The structure also adds `common/{web,idempotency,outbox,messaging}`, `payment/gateway` and
`notification/consumer`.

## §10 Testing plan
| Item | Status | Tests |
|---|---|---|
| Unit: pricing, state transitions, validation, service logic | ✅ | 73 unit tests |
| Repository/integration against PostgreSQL | ✅ | `InventoryRepositoryIT` + all ITs on Testcontainers Postgres |
| Authentication and authorization | ✅ | `AuthApiIT` |
| Concurrent inventory, stock never negative | ✅ | `ConcurrentInventoryIT` |
| Idempotency, no duplicate orders | ✅ | `IdempotencyIT` |
| Redis cache behaviour | ✅ | `ProductCacheIT` |
| RabbitMQ consumer for OrderConfirmedEvent | ✅ | `PaymentAndNotificationIT` |
| API: success, validation failure, unauthorized, not found, conflict | ✅ | API ITs + Postman collection |

## §11 Docker & deployment
| Requirement | Status | Notes |
|---|---|---|
| docker-compose.yml with app, postgres, redis, rabbitmq | ✅ | Plus `web` (nginx + React); all services have health checks; verified `docker compose up` healthy |
| Env vars DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, REDIS_HOST, RABBITMQ_HOST, JWT_SECRET | ✅ | All used in `application.yml` / compose |
| Never commit real secrets; `.env.example` only | ✅ | `.env` is git-ignored (verified untracked); compose fails fast when secrets are missing |

## §12 Implementation sequence
| Step | Status |
|---|---|
| 1–11: project and package structure, Postgres + entities, JWT + roles, product APIs, inventory + cart, orders + state rules, transactions + concurrency, idempotency, Redis, RabbitMQ, tests | ✅ |
| 12: Dockerize the app and supporting services | ✅ |
| 13: Deploy and document architecture, API examples, test results, trade-offs | ⚠️ |

On step 13: the documentation is complete. Deployment was verified locally with Compose. **No public deployment
was made**, because that needs a hosting account and your approval.

## §13 README evidence
| Item | Status |
|---|---|
| Architecture diagram and request-flow diagram | ✅ |
| ER diagram | ✅ |
| API documentation / Postman collection | ✅ (README table, Swagger UI, `postman/`) |
| Concurrency test result and explanation | ✅ |
| Idempotency example | ✅ |
| Redis cache flow | ✅ |
| RabbitMQ event flow | ✅ |
| Docker Compose instructions | ✅ |
| Screenshots | ✅ (catalogue, cart, order detail, notifications) |
| Deployed API/frontend URL | ❌ |
| Known limitations and future microservice decomposition | ✅ |

The deployed URL is ❌ because there is no public deployment; the README has a placeholder for it.

## §14 Resume entry
The blueprint says to use this only once the project is built. Every claim in it (transactional order management
with inventory reservation, payment, cancellation and state workflows; concurrency-safe inventory and idempotent
order creation; Redis caching and RabbitMQ async processing) is implemented and covered by tests listed above.

## Open items needing the owner
1. Push to GitHub (create the remote repo, then `git push`).
2. Deploy to a public host and add the URL to the README.
