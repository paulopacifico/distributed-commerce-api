# Payment Service Design

**Date:** 2026-03-28
**Status:** Approved

---

## Overview

Extends the choreography saga with a Payment Service that processes charges after inventory is successfully reserved. Introduces full compensation when payment fails: the order transitions to `PAYMENT_FAILED` and inventory releases its reservation, returning the system to a clean state.

---

## Saga Flow

```
POST /api/orders
  → order-service        publishes  OrderPlacedEvent          → order-placed-topic
  → inventory-service    consumes   order-placed-topic
                         publishes  InventoryReservedEvent     → inventory-reserved-topic  (success)
                                    InventoryFailedEvent       → inventory-failed-topic    (failure)
  → order-service        consumes   inventory-reserved-topic  → CONFIRMED
                         publishes  OrderConfirmedEvent        → order-confirmed-topic     [NEW]
  → order-service        consumes   inventory-failed-topic    → FAILED (existing)

  → payment-service      consumes   order-confirmed-topic                                 [NEW SERVICE]
                         publishes  PaymentSucceededEvent      → payment-succeeded-topic  [NEW]
                                    PaymentFailedEvent         → payment-failed-topic     [NEW]

  → order-service        consumes   payment-succeeded-topic   → PAID                     [NEW]
  → order-service        consumes   payment-failed-topic      → PAYMENT_FAILED            [NEW]
  → inventory-service    consumes   payment-failed-topic      → release reservation       [NEW]
```

---

## New Service: payment-service

**Language / Framework:** Kotlin 2.1.10, Spring Boot 3.4.3

**Port:** 8083

**Database:** PostgreSQL `payment_db` — tracked via Flyway migrations

### Domain

`PaymentEntity` — one record per processed order:

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `order_id` | BIGINT UNIQUE | idempotency key |
| `order_number` | VARCHAR | |
| `amount` | NUMERIC | price × quantity from `OrderConfirmedEvent` |
| `status` | VARCHAR | `SUCCEEDED` or `FAILED` |
| `failure_reason` | VARCHAR | nullable, populated on failure |
| `processed_at` | TIMESTAMPTZ | |

### Payment simulation

`payment.success-rate` property (Double, 0.0–1.0, default `1.0`). A `Random.nextDouble()` check decides the outcome. Tests set it to `1.0` (always succeed) or `0.0` (always fail) for deterministic assertions.

### Idempotency

A `processed_order_confirmed_events` table (same pattern as inventory-service) prevents duplicate processing if `OrderConfirmedEvent` is redelivered.

### Kafka

| Direction | Topic | Event |
|---|---|---|
| Consumes | `order-confirmed-topic` | `OrderConfirmedEvent` |
| Publishes | `payment-succeeded-topic` | `PaymentSucceededEvent` |
| Publishes | `payment-failed-topic` | `PaymentFailedEvent` |

---

## New Event Contracts

### PaymentSucceededEvent
```kotlin
data class PaymentSucceededEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val amount: BigDecimal,
    val occurredAt: OffsetDateTime
)
```

### PaymentFailedEvent
```kotlin
data class PaymentFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val amount: BigDecimal,
    val reason: String,
    val occurredAt: OffsetDateTime
)
```

---

## Changes to order-service

### OrderConfirmedEvent — add price and quantity
The existing record only carries `eventId`, `orderId`, `orderNumber`, `occurredAt`. Add `price: BigDecimal` and `quantity: Int` so payment-service knows the charge amount.

### Publish OrderConfirmedEvent
`OrderSagaService.confirmOrder()` currently transitions the order to `CONFIRMED` silently. After the status transition, it must publish `OrderConfirmedEvent` to `order-confirmed-topic` (same pattern as `failOrder()` publishing `OrderFailedEvent`).

New classes:
- `OrderConfirmedEventPublisher` interface
- `KafkaOrderConfirmedEventPublisher` implementation

### OrderStatus — add PAID and PAYMENT_FAILED
```java
public enum OrderStatus {
    PENDING, CONFIRMED, FAILED, PAID, PAYMENT_FAILED
}
```

### New consumers
- `PaymentSucceededSagaConsumer` — listens on `payment-succeeded-topic`, calls `orderSagaService.markAsPaid(orderId)`
- `PaymentFailedSagaConsumer` — listens on `payment-failed-topic`, calls `orderSagaService.markAsPaymentFailed(orderId)`

### New OrderSagaService methods
- `markAsPaid(orderId)` — transitions `CONFIRMED → PAID`
- `markAsPaymentFailed(orderId)` — transitions `CONFIRMED → PAYMENT_FAILED`

### New topics in KafkaTopicProperties
`orderConfirmed`, `paymentSucceeded`, `paymentFailed`

---

## Changes to inventory-service

### New consumer: PaymentFailedSagaConsumer
Listens on `payment-failed-topic`. On receipt, calls `inventoryService.releaseInventory(orderId)` — the same method already used by `OrderFailedSagaConsumer`. Idempotent: if no reservation exists for the `orderId`, the call is a no-op.

### New topic in KafkaTopicProperties
`paymentFailed`

---

## Infrastructure

### Docker Compose
`payment_db` logical database added to the existing PostgreSQL `init` script. No new container needed.

### Helm chart
`helm/payment-service/` added with the same structure as the existing charts (`Chart.yaml`, `values.yaml`, `values-dev.yaml`, `values-prod.yaml`, five templates).

### CI
Pull `payment-service` Docker image pre-pull step added. Helm lint + template steps added for the new chart.

---

## Testing

### payment-service integration tests (Kotest, matching inventory-service style)

**`PaymentSagaIntegrationTest`:**

1. `should process payment and publish PaymentSucceededEvent when success rate is 1.0`
   - Sends `OrderConfirmedEvent` to `order-confirmed-topic`
   - Asserts `PaymentEntity` persisted with status `SUCCEEDED`
   - Asserts `PaymentSucceededEvent` published to `payment-succeeded-topic`

2. `should publish PaymentFailedEvent and persist failure record when success rate is 0.0`
   - Sends `OrderConfirmedEvent` to `order-confirmed-topic`
   - Asserts `PaymentEntity` persisted with status `FAILED`
   - Asserts `PaymentFailedEvent` published to `payment-failed-topic` with correct reason

3. `should be idempotent when same OrderConfirmedEvent is delivered twice`
   - Sends the same event twice
   - Asserts only one `PaymentEntity` row exists

### order-service integration tests

- Extend `OrderSagaIntegrationTest` with two new test cases:
  - `shouldMarkOrderAsPaidWhenPaymentSucceeds`
  - `shouldMarkOrderAsPaymentFailedWhenPaymentFails`

### inventory-service integration tests

- Extend `InventorySagaIntegrationTest` with:
  - `should release reserved inventory when payment fails`
