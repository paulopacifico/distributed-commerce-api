# Shipment Service Design

**Date:** 2026-03-31
**Status:** Approved

---

## Overview

Extends the choreography saga with a Shipment Service that processes order dispatch after payment succeeds. Introduces full compensation when shipment fails: the order transitions to `SHIPMENT_FAILED`, the payment is refunded, and inventory releases its reservation — returning the system to a clean state.

---

## Saga Flow

```
POST /api/orders
  → order-service        publishes  OrderPlacedEvent          → order-placed-topic
  → inventory-service    consumes   order-placed-topic
                         publishes  InventoryReservedEvent     → inventory-reserved-topic  (success)
                                    InventoryFailedEvent       → inventory-failed-topic    (failure)
  → order-service        consumes   inventory-reserved-topic  → CONFIRMED
                         publishes  OrderConfirmedEvent        → order-confirmed-topic
  → order-service        consumes   inventory-failed-topic    → FAILED

  → payment-service      consumes   order-confirmed-topic
                         publishes  PaymentSucceededEvent      → payment-succeeded-topic
                                    PaymentFailedEvent         → payment-failed-topic
  → order-service        consumes   payment-succeeded-topic   → PAID
                         publishes  OrderPaidEvent             → order-paid-topic          [NEW]
  → order-service        consumes   payment-failed-topic      → PAYMENT_FAILED
  → inventory-service    consumes   payment-failed-topic      → release reservation

  → shipment-service     consumes   order-paid-topic                                      [NEW SERVICE]
                         publishes  ShipmentShippedEvent       → shipment-shipped-topic   [NEW]
                                    ShipmentFailedEvent        → shipment-failed-topic    [NEW]
  → order-service        consumes   shipment-shipped-topic    → SHIPPED                   [NEW]
  → order-service        consumes   shipment-failed-topic     → SHIPMENT_FAILED            [NEW]
  → payment-service      consumes   shipment-failed-topic     → mark payment REFUNDED     [NEW]
  → inventory-service    consumes   shipment-failed-topic     → release reservation       [NEW]
```

---

## New Service: shipment-service

**Language / Framework:** Kotlin 2.1.10, Spring Boot 3.4.3

**Port:** 8084

**Database:** PostgreSQL `shipment_db` — tracked via Flyway migrations

### Domain

`ShipmentEntity` — one record per processed order:

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGSERIAL PK | |
| `order_id` | BIGINT UNIQUE | idempotency key |
| `order_number` | VARCHAR | |
| `status` | VARCHAR | `PROCESSING`, `SHIPPED`, or `FAILED` |
| `failure_reason` | VARCHAR | nullable, populated on failure |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | set on status transition |

### Shipment simulation

`shipment.success-rate` property (Double, 0.0–1.0, default `1.0`). A `Math.random() < shipment.successRate` check decides the outcome. Consumer persists entity as `PROCESSING`, runs the check, then transitions to `SHIPPED` or `FAILED` before publishing. Tests set it to `1.0` or `0.0` for deterministic assertions.

### Idempotency

A `processed_order_paid_events` table (same pattern as other services) prevents duplicate processing if `OrderPaidEvent` is redelivered.

### Kafka

| Direction | Topic | Event |
|-----------|-------|-------|
| Consumes | `order-paid-topic` | `OrderPaidEvent` |
| Publishes | `shipment-shipped-topic` | `ShipmentShippedEvent` |
| Publishes | `shipment-failed-topic` | `ShipmentFailedEvent` |

---

## New Event Contracts

### OrderPaidEvent (published by order-service)
```kotlin
data class OrderPaidEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val occurredAt: OffsetDateTime
)
```

### ShipmentShippedEvent
```kotlin
data class ShipmentShippedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val occurredAt: OffsetDateTime
)
```

### ShipmentFailedEvent
```kotlin
data class ShipmentFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val reason: String,
    val occurredAt: OffsetDateTime
)
```

---

## Changes to order-service

### OrderStatus — add SHIPPED and SHIPMENT_FAILED
```java
public enum OrderStatus {
    PENDING, CONFIRMED, FAILED, PAID, PAYMENT_FAILED, SHIPPED, SHIPMENT_FAILED
}
```

### OrderEntity — add ship() and failShipment()
Transition methods mirroring existing `pay()` and `failPayment()`.

### Publish OrderPaidEvent
`OrderSagaService.markAsPaid()` currently transitions the order to `PAID` silently. After the status transition, it must publish `OrderPaidEvent` to `order-paid-topic` (same pattern as `confirmOrder()` publishing `OrderConfirmedEvent`).

New classes:
- `OrderPaidEventPublisher` interface
- `KafkaOrderPaidEventPublisher` implementation

### New consumers
- `ShipmentShippedSagaConsumer` — listens on `shipment-shipped-topic`, calls `orderSagaService.markAsShipped(orderId)`
- `ShipmentFailedSagaConsumer` — listens on `shipment-failed-topic`, calls `orderSagaService.markAsShipmentFailed(orderId)`

### New OrderSagaService methods
- `markAsShipped(orderId)` — transitions `PAID → SHIPPED`
- `markAsShipmentFailed(orderId)` — transitions `PAID → SHIPMENT_FAILED`

### New topics in KafkaTopicProperties
`orderPaid`, `shipmentShipped`, `shipmentFailed`

---

## Changes to payment-service

### PaymentStatus — add REFUNDED
`PaymentEntity.status` gains the value `REFUNDED`.

### New method: refundPayment(orderId)
`PaymentService.refundPayment(orderId)` looks up the payment by `orderId` and transitions `SUCCEEDED → REFUNDED`. Idempotent: if payment is already `REFUNDED` or not found, the call is a no-op.

### New consumer: ShipmentFailedSagaConsumer
Listens on `shipment-failed-topic`. Calls `paymentService.refundPayment(orderId)`.
Uses a new `processed_shipment_failed_events` idempotency table.

### New topic in KafkaTopicProperties
`shipmentFailed`

---

## Changes to inventory-service

### New consumer: ShipmentFailedSagaConsumer
Listens on `shipment-failed-topic`. Calls `inventoryService.releaseInventory(orderId)` — the same method used by `PaymentFailedSagaConsumer`. Idempotent: if no reservation exists for the `orderId`, the call is a no-op.

### New topic in KafkaTopicProperties
`shipmentFailed`

---

## Infrastructure

### Docker Compose
`shipment_db` logical database added to the existing PostgreSQL `init` script. No new container needed.

### Helm chart
`helm/shipment-service/` added with the same structure as existing charts (`Chart.yaml`, `values.yaml`, `values-dev.yaml`, `values-prod.yaml`, five templates).

### CI
Pull `shipment-service` Docker image pre-pull step added. Build/test, Docker build, and Helm lint + template steps added for the new chart.

---

## Testing

### shipment-service integration tests (Kotest, matching payment-service style)

**`ShipmentSagaIntegrationTest`** (`shipment.success-rate=1.0`, unique consumer group):

1. `should process shipment and publish ShipmentShippedEvent when success rate is 1.0`
   - Sends `OrderPaidEvent` to `order-paid-topic`
   - Asserts `ShipmentEntity` persisted with status `SHIPPED`
   - Asserts `ShipmentShippedEvent` published to `shipment-shipped-topic`

2. `should be idempotent when same OrderPaidEvent is delivered twice`
   - Sends the same event twice
   - Asserts only one `ShipmentEntity` row exists

**`ShipmentSagaFailureIntegrationTest`** (`shipment.success-rate=0.0`, unique consumer group):

3. `should publish ShipmentFailedEvent and persist failure record when success rate is 0.0`
   - Sends `OrderPaidEvent` to `order-paid-topic`
   - Asserts `ShipmentEntity` persisted with status `FAILED`
   - Asserts `ShipmentFailedEvent` published to `shipment-failed-topic` with correct reason

### order-service integration tests

Extend `OrderSagaIntegrationTest`:
- `shouldMarkOrderAsShippedWhenShipmentSucceeds`
- `shouldMarkOrderAsShipmentFailedWhenShipmentFails`

### payment-service integration tests

Extend `PaymentSagaIntegrationTest`:
- `shouldRefundPaymentWhenShipmentFails`

### inventory-service integration tests

Extend `InventorySagaIntegrationTest`:
- `shouldReleaseReservedInventoryWhenShipmentFails`
