# Test results

Recorded on 2026-10-09 on macOS (arm64), Docker 29, JDK 21. Every number below comes from an actual run.

## Backend — `mvn clean verify`

| Suite | Class | Tests | Failures | Errors |
|---|---|---|---|---|
| Unit | `auth.service.AuthServiceTest` | 5 | 0 | 0 |
| Unit | `common.RequestHasherTest` | 2 | 0 | 0 |
| Unit | `common.RequestValidationTest` | 4 | 0 | 0 |
| Unit | `inventory.service.InventoryServiceTest` | 3 | 0 | 0 |
| Unit | `notification.service.NotificationServiceTest` | 2 | 0 | 0 |
| Unit | `order.domain.OrderStatusTest` | 28 | 0 | 0 |
| Unit | `order.domain.OrderTest` | 5 | 0 | 0 |
| Unit | `order.service.OrderServiceTest` | 11 | 0 | 0 |
| Unit | `order.service.PricingServiceTest` | 6 | 0 | 0 |
| Unit | `payment.service.PaymentServiceTest` | 7 | 0 | 0 |
| Integration | `auth.AuthApiIT` | 11 | 0 | 0 |
| Integration | `cart.CartApiIT` | 6 | 0 | 0 |
| Integration | `inventory.ConcurrentInventoryIT` | 4 | 0 | 0 |
| Integration | `inventory.InventoryApiIT` | 2 | 0 | 0 |
| Integration | `inventory.InventoryRepositoryIT` | 5 | 0 | 0 |
| Integration | `order.IdempotencyIT` | 8 | 0 | 0 |
| Integration | `order.OrderApiIT` | 10 | 0 | 0 |
| Integration | `payment.PaymentAndNotificationIT` | 8 | 0 | 0 |
| Integration | `product.ProductApiIT` | 8 | 0 | 0 |
| Integration | `product.ProductCacheIT` | 5 | 0 | 0 |

**Total: 140 tests, 0 failures, 0 errors.** Integration tests run against real PostgreSQL 16, Redis 7 and RabbitMQ 3.13 containers (Testcontainers).

## Frontend — `npm test -- --run` (Vitest)

```
 ✓ src/utils/idempotency.test.js (7 tests) 2ms
 ✓ src/utils/format.test.js (5 tests) 1ms
 ✓ src/api/client.test.js (8 tests) 6ms
 ✓ src/components/StatusTimeline.test.jsx (2 tests) 21ms
 Test Files  4 passed (4)
      Tests  22 passed (22)
```

## API contract — Postman collection via Newman (Docker Compose stack, through nginx)

```
  ✓  Envelope has requestId
  ✓  Status 201
  ✓  Returns JWT
  ✓  Role is CUSTOMER
  ✓  Envelope has requestId
  ✓  Status 409
  ✓  Error code EMAIL_ALREADY_REGISTERED
  ✓  Envelope has requestId
  ✓  Status 400
  ✓  Error code VALIDATION_FAILED
  ✓  Field errors listed
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 401
  ✓  Error code INVALID_CREDENTIALS
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Role is ADMIN
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Email matches
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 201
  ✓  Envelope has requestId
  ✓  Status 201
  ✓  Location header
  ✓  Envelope has requestId
  ✓  Status 403
  ✓  Error code FORBIDDEN
  ✓  Envelope has requestId
  ✓  Status 400
  ✓  Error code VALIDATION_FAILED
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Page metadata
  ✓  Live stock merged
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  5 in stock
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Fresh price after eviction
  ✓  Envelope has requestId
  ✓  Status 404
  ✓  Error code NOT_FOUND
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Subtotal
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  One line
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Qty 3
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Empty
  ✓  Envelope has requestId
  ✓  Status 401
  ✓  Error code UNAUTHORIZED
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 400
  ✓  Error code IDEMPOTENCY_KEY_REQUIRED
  ✓  Envelope has requestId
  ✓  Status 201
  ✓  Status CREATED
  ✓  Total = 2 x 59.99
  ✓  Not a replay
  ✓  Envelope has requestId
  ✓  Status 201
  ✓  Same order id
  ✓  Replayed header
  ✓  Envelope has requestId
  ✓  Status 422
  ✓  Error code IDEMPOTENCY_KEY_REUSED
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  available = 3
  ✓  Envelope has requestId
  ✓  Status 409
  ✓  Error code OUT_OF_STOCK
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Has orders
  ✓  Envelope has requestId
  ✓  Status 402
  ✓  Error code PAYMENT_DECLINED
  ✓  Order still payable
  ✓  Envelope has requestId
  ✓  Status 201
  ✓  SUCCEEDED
  ✓  Order CONFIRMED
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Same transaction
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  2 attempts
  ✓  Envelope has requestId
  ✓  Status 409
  ✓  Error code INVALID_STATE_TRANSITION
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  EMAIL + SMS sent
  ✓  Envelope has requestId
  ✓  Status 201
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  CANCELLED
  ✓  Envelope has requestId
  ✓  Status 409
  ✓  Error code INVALID_STATE_TRANSITION
  ✓  Envelope has requestId
  ✓  Status 409
  ✓  Error code ORDER_CANCELLED
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 403
  ✓  Envelope has requestId
  ✓  Status 422
  ✓  Error code STATUS_NOT_MANUALLY_SETTABLE
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 409
  ✓  Error code INVALID_STATE_TRANSITION
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  6 status changes
  ✓  Envelope has requestId
  ✓  Status 409
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  2 sold, 0 reserved, 3 available
  ✓  Envelope has requestId
  ✓  Status 200
  ✓  13 available
  ✓  Envelope has requestId
  ✓  Status 409
  ✓  Error code STALE_INVENTORY_VERSION
  ✓  Envelope has requestId
  ✓  Status 204
  ✓  Envelope has requestId
  ✓  Status 404
│                         │          executed │           failed │
│                requests │                55 │                0 │
│            test-scripts │               110 │                0 │
│              assertions │               158 │                0 │
```

## Live concurrency demo — `scripts/concurrency-demo.sh` against the compose stack

```
Product 4 ('Limited Edition Console') available before: 1
Registering 25 buyers...
Firing 25 concurrent orders for 1 unit each...
HTTP status distribution:
     1 201
    24 409
Available after: 0 (never negative)
```
