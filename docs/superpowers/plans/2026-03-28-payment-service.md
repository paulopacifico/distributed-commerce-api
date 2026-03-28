# Payment Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the choreography saga with a payment-service that charges after inventory reservation, plus full compensation (inventory release) when payment fails.

**Architecture:** Sequential saga — order-service publishes `OrderConfirmedEvent` after inventory success; payment-service processes the charge and publishes `PaymentSucceededEvent` or `PaymentFailedEvent`; order-service transitions to `PAID` or `PAYMENT_FAILED`; inventory-service releases reservation on payment failure. All consumers are idempotent via processed-event tables.

**Tech Stack:** Kotlin 2.1.10, Spring Boot 3.4.3, Spring Kafka, PostgreSQL + Flyway, Kotest 5.9.1 + Testcontainers, Maven.

---

## File Map

### New files — payment-service
| File | Purpose |
|---|---|
| `payment-service/pom.xml` | Maven build — mirrors inventory-service structure |
| `payment-service/src/main/kotlin/…/PaymentServiceApplication.kt` | Spring Boot entry point |
| `payment-service/src/main/resources/application.yml` | App configuration |
| `payment-service/src/main/resources/db/migration/V1__create_payments_table.sql` | `payments` table |
| `payment-service/src/main/resources/db/migration/V2__create_processed_order_confirmed_events_table.sql` | Idempotency table |
| `payment-service/src/main/kotlin/…/messaging/api/OrderConfirmedEvent.kt` | Inbound event contract |
| `payment-service/src/main/kotlin/…/messaging/api/PaymentSucceededEvent.kt` | Outbound success event |
| `payment-service/src/main/kotlin/…/messaging/api/PaymentFailedEvent.kt` | Outbound failure event |
| `payment-service/src/main/kotlin/…/messaging/config/KafkaTopicProperties.kt` | Topic name config |
| `payment-service/src/main/kotlin/…/messaging/config/PaymentKafkaConfiguration.kt` | Kafka beans |
| `payment-service/src/main/kotlin/…/payment/domain/PaymentEntity.kt` | JPA entity |
| `payment-service/src/main/kotlin/…/payment/application/PaymentRepository.kt` | Spring Data JPA repo |
| `payment-service/src/main/kotlin/…/payment/messaging/persistence/ProcessedOrderConfirmedEventEntity.kt` | Idempotency JPA entity |
| `payment-service/src/main/kotlin/…/payment/messaging/persistence/ProcessedOrderConfirmedEventRepository.kt` | Idempotency repo |
| `payment-service/src/main/kotlin/…/payment/application/PaymentProperties.kt` | `payment.success-rate` |
| `payment-service/src/main/kotlin/…/payment/application/PaymentService.kt` | Core payment logic |
| `payment-service/src/main/kotlin/…/payment/messaging/OrderConfirmedSagaConsumer.kt` | Kafka listener |
| `payment-service/src/main/kotlin/…/payment/messaging/PaymentSagaEventPublisher.kt` | Kafka publisher |
| `payment-service/Dockerfile` | Multi-stage container build |
| `payment-service/src/test/kotlin/…/support/AbstractIntegrationTest.kt` | Testcontainers base |
| `payment-service/src/test/kotlin/…/payment/PaymentSagaIntegrationTest.kt` | 3 integration test cases |
| `payment-service/src/test/resources/application-test.yml` | Test profile config |

### New files — Helm chart
| File | Purpose |
|---|---|
| `helm/payment-service/Chart.yaml` | Chart metadata |
| `helm/payment-service/values.yaml` | Default values |
| `helm/payment-service/values-dev.yaml` | Dev overrides |
| `helm/payment-service/values-prod.yaml` | Prod overrides |
| `helm/payment-service/templates/_helpers.tpl` | Named templates |
| `helm/payment-service/templates/configmap.yaml` | ConfigMap |
| `helm/payment-service/templates/secret.yaml` | Secret |
| `helm/payment-service/templates/deployment.yaml` | Deployment |
| `helm/payment-service/templates/service.yaml` | Service |
| `helm/payment-service/templates/hpa.yaml` | HPA |

### Modified files
| File | Change |
|---|---|
| `order-service/…/order/api/OrderConfirmedEvent.java` | Add `price` and `quantity` fields |
| `order-service/…/order/domain/OrderStatus.java` | Add `PAID`, `PAYMENT_FAILED` |
| `order-service/…/order/domain/OrderEntity.java` | Add `pay()`, `failPayment()` domain methods |
| `order-service/…/messaging/config/KafkaTopicProperties.java` | Add `orderConfirmed`, `paymentSucceeded`, `paymentFailed` |
| `order-service/…/messaging/config/OrderKafkaConfiguration.java` | Register 3 new topic beans + 2 DLT beans |
| `order-service/…/order/application/OrderSagaService.java` | Inject publisher, publish in `confirmOrder()`, add `markAsPaid()`, `markAsPaymentFailed()` |
| `order-service/…/order/messaging/InventorySagaConsumer.java` | No change needed (confirmOrder already calls service) |
| `order-service/…/order/application/OrderService.java` | Update `updateStatus` to handle new enum values |
| `order-service/…/messaging/config/OrderKafkaConfiguration.java` | Already handles topics; add new topic beans |
| `order-service/src/main/resources/application.yml` | Add 3 new topic env vars |
| `order-service/src/main/resources/db/migration/V4__add_payment_statuses.sql` | Extend `status` column length if needed (already VARCHAR(16) — PAYMENT_FAILED is 15 chars, OK) |
| `inventory-service/…/messaging/config/KafkaTopicProperties.kt` | Add `paymentFailed` field |
| `inventory-service/…/messaging/config/InventoryKafkaConfiguration.kt` | Register `paymentFailed` topic bean |
| `inventory-service/src/main/resources/application.yml` | Add `payment-failed` topic env var |
| `docker/postgres/init/01-create-databases.sql` | Add `CREATE DATABASE payment_db;` |
| `.github/workflows/ci-cd.yml` | Add payment-service Docker pre-pull, build, test, Helm lint/template steps |

---

## Task 1: Extend OrderConfirmedEvent with price and quantity

**Files:**
- Modify: `order-service/src/main/java/com/paulopacifico/orderservice/order/api/OrderConfirmedEvent.java`

- [ ] **Step 1: Update the record**

Replace the file contents with:

```java
package com.paulopacifico.orderservice.order.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        BigDecimal price,
        Integer quantity,
        OffsetDateTime occurredAt
) {
}
```

- [ ] **Step 2: Verify compilation**

```bash
cd order-service && mvn -B compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add order-service/src/main/java/com/paulopacifico/orderservice/order/api/OrderConfirmedEvent.java
git commit -m "Add price and quantity to OrderConfirmedEvent"
```

---

## Task 2: Extend OrderStatus and OrderEntity

**Files:**
- Modify: `order-service/src/main/java/com/paulopacifico/orderservice/order/domain/OrderStatus.java`
- Modify: `order-service/src/main/java/com/paulopacifico/orderservice/order/domain/OrderEntity.java`

- [ ] **Step 1: Add PAID and PAYMENT_FAILED to the enum**

```java
package com.paulopacifico.orderservice.order.domain;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    PAID,
    PAYMENT_FAILED
}
```

- [ ] **Step 2: Add pay() and failPayment() to OrderEntity**

In `OrderEntity.java`, add after the existing `fail()` method (line 97):

```java
    public void pay() {
        this.status = OrderStatus.PAID;
    }

    public void failPayment() {
        this.status = OrderStatus.PAYMENT_FAILED;
    }
```

- [ ] **Step 3: Verify compilation**

```bash
cd order-service && mvn -B compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add order-service/src/main/java/com/paulopacifico/orderservice/order/domain/OrderStatus.java
git add order-service/src/main/java/com/paulopacifico/orderservice/order/domain/OrderEntity.java
git commit -m "Add PAID and PAYMENT_FAILED statuses and domain methods to OrderEntity"
```

---

## Task 3: Add new Kafka topics to order-service

**Files:**
- Modify: `order-service/src/main/java/com/paulopacifico/orderservice/messaging/config/KafkaTopicProperties.java`
- Modify: `order-service/src/main/java/com/paulopacifico/orderservice/messaging/config/OrderKafkaConfiguration.java`
- Modify: `order-service/src/main/resources/application.yml`

- [ ] **Step 1: Extend KafkaTopicProperties**

```java
package com.paulopacifico.orderservice.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicProperties(
        String orderPlaced,
        String inventoryReserved,
        String inventoryFailed,
        String orderFailed,
        String orderConfirmed,
        String paymentSucceeded,
        String paymentFailed
) {
}
```

- [ ] **Step 2: Register new topic beans in OrderKafkaConfiguration**

In `OrderKafkaConfiguration.java`, add after the existing `orderFailedDltTopic` bean (line 111):

```java
    @Bean
    public NewTopic orderConfirmedTopic(KafkaTopicProperties topics) {
        return name(topics.orderConfirmed()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentSucceededTopic(KafkaTopicProperties topics) {
        return name(topics.paymentSucceeded()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentSucceededDltTopic(KafkaTopicProperties topics) {
        return name(topics.paymentSucceeded() + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic(KafkaTopicProperties topics) {
        return name(topics.paymentFailed()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedDltTopic(KafkaTopicProperties topics) {
        return name(topics.paymentFailed() + ".DLT").partitions(3).replicas(1).build();
    }
```

- [ ] **Step 3: Add topic config to application.yml**

In `order-service/src/main/resources/application.yml`, under `app.kafka.topics`, add after `order-failed`:

```yaml
      order-confirmed: ${ORDER_CONFIRMED_TOPIC:order-confirmed-topic}
      payment-succeeded: ${PAYMENT_SUCCEEDED_TOPIC:payment-succeeded-topic}
      payment-failed: ${PAYMENT_FAILED_TOPIC:payment-failed-topic}
```

- [ ] **Step 4: Verify compilation**

```bash
cd order-service && mvn -B compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add order-service/src/main/java/com/paulopacifico/orderservice/messaging/config/KafkaTopicProperties.java
git add order-service/src/main/java/com/paulopacifico/orderservice/messaging/config/OrderKafkaConfiguration.java
git add order-service/src/main/resources/application.yml
git commit -m "Add orderConfirmed, paymentSucceeded, paymentFailed topics to order-service"
```

---

## Task 4: Create OrderConfirmedEventPublisher and update OrderSagaService

**Files:**
- Create: `order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/OrderConfirmedEventPublisher.java`
- Create: `order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/KafkaOrderConfirmedEventPublisher.java`
- Modify: `order-service/src/main/java/com/paulopacifico/orderservice/order/application/OrderSagaService.java`

- [ ] **Step 1: Create the publisher interface**

```java
package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.order.api.OrderConfirmedEvent;

public interface OrderConfirmedEventPublisher {
    void publish(OrderConfirmedEvent event);
}
```

- [ ] **Step 2: Create the Kafka implementation**

```java
package com.paulopacifico.orderservice.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulopacifico.orderservice.messaging.config.KafkaTopicProperties;
import com.paulopacifico.orderservice.order.api.OrderConfirmedEvent;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderConfirmedEventPublisher implements OrderConfirmedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderConfirmedEventPublisher.class);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopicProperties topics;

    public KafkaOrderConfirmedEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            KafkaTopicProperties topics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topics = topics;
    }

    @Override
    public void publish(OrderConfirmedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topics.orderConfirmed(), event.orderNumber(), payload)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info(
                    "Published OrderConfirmedEvent eventId={} orderId={} orderNumber={} topic={}",
                    event.eventId(),
                    event.orderId(),
                    event.orderNumber(),
                    topics.orderConfirmed()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OrderConfirmedEvent for orderNumber=%s".formatted(event.orderNumber()), exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to publish OrderConfirmedEvent for orderNumber=%s".formatted(event.orderNumber()),
                    exception
            );
        }
    }
}
```

- [ ] **Step 3: Update OrderSagaService**

Replace the full file content with:

```java
package com.paulopacifico.orderservice.order.application;

import com.paulopacifico.orderservice.messaging.api.OrderFailedEvent;
import com.paulopacifico.orderservice.order.api.OrderConfirmedEvent;
import com.paulopacifico.orderservice.order.api.OrderMapper;
import com.paulopacifico.orderservice.order.api.OrderResponse;
import com.paulopacifico.orderservice.order.domain.OrderEntity;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import com.paulopacifico.orderservice.order.messaging.OrderConfirmedEventPublisher;
import com.paulopacifico.orderservice.order.messaging.OrderFailedEventPublisher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderFailedEventPublisher orderFailedEventPublisher;
    private final OrderConfirmedEventPublisher orderConfirmedEventPublisher;

    public OrderSagaService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            OrderFailedEventPublisher orderFailedEventPublisher,
            OrderConfirmedEventPublisher orderConfirmedEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.orderFailedEventPublisher = orderFailedEventPublisher;
        this.orderConfirmedEventPublisher = orderConfirmedEventPublisher;
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus status) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        applyStatusTransition(order, status);
        return orderMapper.toResponse(order);
    }

    @Transactional
    public void confirmOrder(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyStatusTransition(order, OrderStatus.CONFIRMED);
        if (transitioned) {
            var event = new OrderConfirmedEvent(
                    UUID.randomUUID(),
                    order.getId(),
                    order.getOrderNumber(),
                    order.getPrice(),
                    order.getQuantity(),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            orderConfirmedEventPublisher.publish(event);
            log.info("Published OrderConfirmedEvent eventId={} orderId={} orderNumber={}", event.eventId(), order.getId(), order.getOrderNumber());
        }
        log.info("Marked order id={} orderNumber={} as CONFIRMED", order.getId(), order.getOrderNumber());
    }

    @Transactional
    public void failOrder(Long orderId, String reason) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyStatusTransition(order, OrderStatus.FAILED);
        if (transitioned) {
            var event = new OrderFailedEvent(
                    UUID.randomUUID(),
                    order.getId(),
                    order.getOrderNumber(),
                    order.getSkuCode(),
                    order.getQuantity(),
                    reason,
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            orderFailedEventPublisher.publish(event);
            log.info("Published OrderFailedEvent eventId={} orderId={} orderNumber={}", event.eventId(), order.getId(), order.getOrderNumber());
        }
        log.info("Marked order id={} orderNumber={} as FAILED reason={}", order.getId(), order.getOrderNumber(), reason);
    }

    @Transactional
    public void markAsPaid(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyPaymentTransition(order, OrderStatus.PAID);
        if (transitioned) {
            log.info("Marked order id={} orderNumber={} as PAID", order.getId(), order.getOrderNumber());
        }
    }

    @Transactional
    public void markAsPaymentFailed(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyPaymentTransition(order, OrderStatus.PAYMENT_FAILED);
        if (transitioned) {
            log.info("Marked order id={} orderNumber={} as PAYMENT_FAILED", order.getId(), order.getOrderNumber());
        }
    }

    private boolean applyStatusTransition(OrderEntity order, OrderStatus status) {
        if (order.getStatus() == status) {
            log.info(
                    "Ignoring duplicate order status transition id={} orderNumber={} status={}",
                    order.getId(), order.getOrderNumber(), status
            );
            return false;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn(
                    "Ignoring out-of-order status transition id={} orderNumber={} currentStatus={} requestedStatus={}",
                    order.getId(), order.getOrderNumber(), order.getStatus(), status
            );
            return false;
        }

        if (status == OrderStatus.CONFIRMED) {
            order.confirm();
        } else if (status == OrderStatus.FAILED) {
            order.fail();
        }

        log.info("Updated order id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), order.getStatus());
        return true;
    }

    private boolean applyPaymentTransition(OrderEntity order, OrderStatus target) {
        if (order.getStatus() == target) {
            log.info(
                    "Ignoring duplicate payment status transition id={} orderNumber={} status={}",
                    order.getId(), order.getOrderNumber(), target
            );
            return false;
        }

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn(
                    "Ignoring payment transition for order not in CONFIRMED state id={} orderNumber={} currentStatus={} requestedStatus={}",
                    order.getId(), order.getOrderNumber(), order.getStatus(), target
            );
            return false;
        }

        if (target == OrderStatus.PAID) {
            order.pay();
        } else if (target == OrderStatus.PAYMENT_FAILED) {
            order.failPayment();
        }

        log.info("Updated order id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), order.getStatus());
        return true;
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd order-service && mvn -B compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/OrderConfirmedEventPublisher.java
git add order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/KafkaOrderConfirmedEventPublisher.java
git add order-service/src/main/java/com/paulopacifico/orderservice/order/application/OrderSagaService.java
git commit -m "Publish OrderConfirmedEvent on inventory reservation and add payment status transitions"
```

---

## Task 5: Add PaymentSagaConsumer to order-service

**Files:**
- Create: `order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/PaymentSagaConsumer.java`
- Create: `order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/persistence/ProcessedPaymentEventEntity.java`
- Create: `order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/persistence/ProcessedPaymentEventRepository.java`
- Create: `order-service/src/main/java/com/paulopacifico/orderservice/messaging/api/PaymentSucceededEvent.java`
- Create: `order-service/src/main/java/com/paulopacifico/orderservice/messaging/api/PaymentFailedEvent.java`
- Create: `order-service/src/main/resources/db/migration/V4__create_processed_payment_events_table.sql`

- [ ] **Step 1: Create PaymentSucceededEvent**

```java
package com.paulopacifico.orderservice.messaging.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        OffsetDateTime occurredAt
) {
}
```

- [ ] **Step 2: Create PaymentFailedEvent**

```java
package com.paulopacifico.orderservice.messaging.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        String reason,
        OffsetDateTime occurredAt
) {
}
```

- [ ] **Step 3: Create ProcessedPaymentEventEntity**

```java
package com.paulopacifico.orderservice.order.messaging.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_payment_events")
public class ProcessedPaymentEventEntity {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedPaymentEventEntity() {}

    public ProcessedPaymentEventEntity(UUID eventId, Long orderId, OffsetDateTime processedAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.processedAt = processedAt;
    }
}
```

- [ ] **Step 4: Create ProcessedPaymentEventRepository**

```java
package com.paulopacifico.orderservice.order.messaging.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEventEntity, UUID> {
}
```

- [ ] **Step 5: Create the Flyway migration**

File: `order-service/src/main/resources/db/migration/V4__create_processed_payment_events_table.sql`

```sql
CREATE TABLE processed_payment_events (
    event_id     UUID                     NOT NULL PRIMARY KEY,
    order_id     BIGINT                   NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

- [ ] **Step 6: Create PaymentSagaConsumer**

```java
package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.messaging.api.PaymentFailedEvent;
import com.paulopacifico.orderservice.messaging.api.PaymentSucceededEvent;
import com.paulopacifico.orderservice.order.application.OrderSagaService;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedPaymentEventEntity;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedPaymentEventRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaConsumer.class);

    private final OrderSagaService orderSagaService;
    private final ProcessedPaymentEventRepository processedPaymentEventRepository;

    public PaymentSagaConsumer(
            OrderSagaService orderSagaService,
            ProcessedPaymentEventRepository processedPaymentEventRepository
    ) {
        this.orderSagaService = orderSagaService;
        this.processedPaymentEventRepository = processedPaymentEventRepository;
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.payment-succeeded}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentSucceeded(PaymentSucceededEvent event) {
        if (processedPaymentEventRepository.existsById(event.eventId())) {
            log.info("Ignoring duplicate PaymentSucceededEvent eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }

        orderSagaService.markAsPaid(event.orderId());
        processedPaymentEventRepository.save(
                new ProcessedPaymentEventEntity(event.eventId(), event.orderId(), OffsetDateTime.now())
        );
        log.info("Processed PaymentSucceededEvent eventId={} orderId={}", event.eventId(), event.orderId());
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.payment-failed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentFailed(PaymentFailedEvent event) {
        if (processedPaymentEventRepository.existsById(event.eventId())) {
            log.info("Ignoring duplicate PaymentFailedEvent eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }

        orderSagaService.markAsPaymentFailed(event.orderId());
        processedPaymentEventRepository.save(
                new ProcessedPaymentEventEntity(event.eventId(), event.orderId(), OffsetDateTime.now())
        );
        log.info("Processed PaymentFailedEvent eventId={} orderId={} reason={}", event.eventId(), event.orderId(), event.reason());
    }
}
```

- [ ] **Step 7: Verify compilation**

```bash
cd order-service && mvn -B compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
git add order-service/src/main/java/com/paulopacifico/orderservice/messaging/api/PaymentSucceededEvent.java
git add order-service/src/main/java/com/paulopacifico/orderservice/messaging/api/PaymentFailedEvent.java
git add order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/persistence/ProcessedPaymentEventEntity.java
git add order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/persistence/ProcessedPaymentEventRepository.java
git add order-service/src/main/java/com/paulopacifico/orderservice/order/messaging/PaymentSagaConsumer.java
git add order-service/src/main/resources/db/migration/V4__create_processed_payment_events_table.sql
git commit -m "Add PaymentSagaConsumer to order-service to handle payment outcomes"
```

---

## Task 6: Add order-service integration tests for payment outcomes

**Files:**
- Modify: `order-service/src/test/java/com/paulopacifico/orderservice/order/OrderPlacedEventIntegrationTest.java` (add two new test methods, or create a separate test class — separate class preferred to keep each test file focused)
- Create: `order-service/src/test/java/com/paulopacifico/orderservice/order/OrderSagaIntegrationTest.java`

- [ ] **Step 1: Create OrderSagaIntegrationTest**

```java
package com.paulopacifico.orderservice.order;

import com.paulopacifico.orderservice.messaging.api.PaymentFailedEvent;
import com.paulopacifico.orderservice.messaging.api.PaymentSucceededEvent;
import com.paulopacifico.orderservice.order.api.CreateOrderRequest;
import com.paulopacifico.orderservice.order.api.OrderResponse;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedPaymentEventRepository;
import com.paulopacifico.orderservice.support.AbstractIntegrationTest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderSagaIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProcessedPaymentEventRepository processedPaymentEventRepository;

    @Autowired
    private com.paulopacifico.orderservice.order.application.OrderRepository orderRepository;

    @Test
    void shouldMarkOrderAsPaidWhenPaymentSucceeds() throws Exception {
        // Create order to get a valid orderId
        var response = testRestTemplate.postForEntity(
                "http://localhost:" + port + "/api/orders",
                new CreateOrderRequest("SKU-PAY-200", new BigDecimal("29.99"), 2),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var orderResponse = objectMapper.readValue(response.getBody(), OrderResponse.class);
        Long orderId = orderResponse.id();
        String orderNumber = orderResponse.orderNumber();

        // Manually transition to CONFIRMED (simulating inventory reservation)
        var order = orderRepository.findById(orderId).orElseThrow();
        order.confirm();
        orderRepository.save(order);

        // Publish PaymentSucceededEvent
        var event = new PaymentSucceededEvent(
                UUID.randomUUID(),
                orderId,
                orderNumber,
                new BigDecimal("59.98"),
                OffsetDateTime.now()
        );
        kafkaTemplate.send(new ProducerRecord<>(
                "payment-succeeded-topic",
                orderNumber,
                objectMapper.writeValueAsString(event)
        )).get();

        // Assert order transitions to PAID
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    var updated = orderRepository.findById(orderId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
                });
    }

    @Test
    void shouldMarkOrderAsPaymentFailedWhenPaymentFails() throws Exception {
        // Create order
        var response = testRestTemplate.postForEntity(
                "http://localhost:" + port + "/api/orders",
                new CreateOrderRequest("SKU-PAY-300", new BigDecimal("15.00"), 1),
                String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var orderResponse = objectMapper.readValue(response.getBody(), OrderResponse.class);
        Long orderId = orderResponse.id();
        String orderNumber = orderResponse.orderNumber();

        // Manually transition to CONFIRMED
        var order = orderRepository.findById(orderId).orElseThrow();
        order.confirm();
        orderRepository.save(order);

        // Publish PaymentFailedEvent
        var event = new PaymentFailedEvent(
                UUID.randomUUID(),
                orderId,
                orderNumber,
                new BigDecimal("15.00"),
                "Simulated payment failure",
                OffsetDateTime.now()
        );
        kafkaTemplate.send(new ProducerRecord<>(
                "payment-failed-topic",
                orderNumber,
                objectMapper.writeValueAsString(event)
        )).get();

        // Assert order transitions to PAYMENT_FAILED
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    var updated = orderRepository.findById(orderId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
                });
    }
}
```

- [ ] **Step 2: Make OrderRepository visible to test** (it's already package-private in the application layer; the test is in the same logical module — ensure `OrderRepository` is `public`)

Check `order-service/src/main/java/com/paulopacifico/orderservice/order/application/OrderRepository.java` — it should be:

```java
package com.paulopacifico.orderservice.order.application;

import com.paulopacifico.orderservice.order.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
}
```

- [ ] **Step 3: Run the new tests**

```bash
cd order-service && mvn -B test -Dtest=OrderSagaIntegrationTest -Dspring.profiles.active=test
```

Expected: `BUILD SUCCESS`, 2 tests pass.

- [ ] **Step 4: Commit**

```bash
git add order-service/src/test/java/com/paulopacifico/orderservice/order/OrderSagaIntegrationTest.java
git commit -m "Add integration tests for payment success and failure saga transitions in order-service"
```

---

## Task 7: Add PaymentFailedSagaConsumer to inventory-service

**Files:**
- Modify: `inventory-service/src/main/kotlin/com/paulopacifico/inventoryservice/messaging/config/KafkaTopicProperties.kt`
- Modify: `inventory-service/src/main/kotlin/com/paulopacifico/inventoryservice/messaging/config/InventoryKafkaConfiguration.kt`
- Modify: `inventory-service/src/main/resources/application.yml`
- Create: `inventory-service/src/main/kotlin/com/paulopacifico/inventoryservice/messaging/api/PaymentFailedEvent.kt`
- Create: `inventory-service/src/main/kotlin/com/paulopacifico/inventoryservice/inventory/messaging/PaymentFailedSagaConsumer.kt`

- [ ] **Step 1: Add paymentFailed to KafkaTopicProperties**

```kotlin
package com.paulopacifico.inventoryservice.messaging.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.kafka.topics")
data class KafkaTopicProperties(
    var orderPlaced: String = "order-placed-topic",
    var inventoryReserved: String = "inventory-reserved-topic",
    var inventoryFailed: String = "inventory-failed-topic",
    var orderFailed: String = "order-failed-topic",
    var paymentFailed: String = "payment-failed-topic",
)
```

- [ ] **Step 2: Register paymentFailedTopic bean in InventoryKafkaConfiguration**

In `InventoryKafkaConfiguration.kt`, add after the existing `orderFailedTopic` bean:

```kotlin
    @Bean
    fun paymentFailedTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.paymentFailed).partitions(3).replicas(1).build()
```

- [ ] **Step 3: Add topic env var to application.yml**

In `inventory-service/src/main/resources/application.yml`, under `app.kafka.topics`, add after `order-failed`:

```yaml
      payment-failed: ${PAYMENT_FAILED_TOPIC:payment-failed-topic}
```

- [ ] **Step 4: Create PaymentFailedEvent data class**

```kotlin
package com.paulopacifico.inventoryservice.messaging.api

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class PaymentFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val amount: BigDecimal,
    val reason: String,
    val occurredAt: OffsetDateTime,
)
```

- [ ] **Step 5: Create PaymentFailedSagaConsumer**

```kotlin
package com.paulopacifico.inventoryservice.inventory.messaging

import com.paulopacifico.inventoryservice.inventory.application.InventoryService
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.InventoryReservationRepository
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventEntity
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventRepository
import com.paulopacifico.inventoryservice.messaging.api.PaymentFailedEvent
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFailedSagaConsumer(
    private val inventoryService: InventoryService,
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val processedOrderEventRepository: ProcessedOrderEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @KafkaListener(
        id = "paymentFailedSagaConsumer",
        topics = ["\${app.kafka.topics.payment-failed}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumePaymentFailed(event: PaymentFailedEvent) {
        if (processedOrderEventRepository.existsById(event.eventId)) {
            logger.info("Ignoring duplicate PaymentFailedEvent eventId={} orderId={}", event.eventId, event.orderId)
            return
        }

        val reservation = inventoryReservationRepository.findById(event.orderId).orElse(null)
        if (reservation == null) {
            logger.warn(
                "No reservation found for orderId={}, skipping compensation on payment failure",
                event.orderId,
            )
        } else {
            inventoryService.releaseInventory(reservation.skuCode, reservation.reservedQuantity)
            inventoryReservationRepository.delete(reservation)
        }

        processedOrderEventRepository.save(
            ProcessedOrderEventEntity(
                eventId = event.eventId,
                orderId = event.orderId,
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
        logger.info("Processed PaymentFailedEvent eventId={} orderId={} reason={}", event.eventId, event.orderId, event.reason)
    }
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd inventory-service && mvn -B compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add inventory-service/src/main/kotlin/com/paulopacifico/inventoryservice/messaging/config/KafkaTopicProperties.kt
git add inventory-service/src/main/kotlin/com/paulopacifico/inventoryservice/messaging/config/InventoryKafkaConfiguration.kt
git add inventory-service/src/main/resources/application.yml
git add inventory-service/src/main/kotlin/com/paulopacifico/inventoryservice/messaging/api/PaymentFailedEvent.kt
git add inventory-service/src/main/kotlin/com/paulopacifico/inventoryservice/inventory/messaging/PaymentFailedSagaConsumer.kt
git commit -m "Add PaymentFailedSagaConsumer to inventory-service to release reservation on payment failure"
```

---

## Task 8: Add inventory-service integration test for payment failure compensation

**Files:**
- Modify: `inventory-service/src/test/kotlin/com/paulopacifico/inventoryservice/inventory/InventorySagaIntegrationTest.kt`

- [ ] **Step 1: Add the new test case**

Inside the `init { }` block of `InventorySagaIntegrationTest`, after the existing `"should release reserved inventory when order fails"` test, add:

```kotlin
        "should release reserved inventory when payment fails" {
            awaitTopicReady("payment-failed-topic")

            inventoryRepository.saveAndFlush(
                InventoryEntity(skuCode = "SKU-KT-400", quantity = 5),
            )
            inventoryReservationRepository.saveAndFlush(
                InventoryReservationEntity(
                    orderId = 404L,
                    skuCode = "SKU-KT-400",
                    reservedQuantity = 3,
                    reservedAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )

            val listenerContainer = requireNotNull(
                kafkaListenerEndpointRegistry.getListenerContainer("paymentFailedSagaConsumer"),
            ) { "Kafka listener container paymentFailedSagaConsumer was not registered" }
            ContainerTestUtils.waitForAssignment(listenerContainer, 3)

            val paymentFailedEvent = com.paulopacifico.inventoryservice.messaging.api.PaymentFailedEvent(
                eventId = UUID.randomUUID(),
                orderId = 404L,
                orderNumber = "ORD-KT-404",
                amount = java.math.BigDecimal("49.95"),
                reason = "Insufficient funds",
                occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
            kafkaTemplate.send(
                ProducerRecord("payment-failed-topic", paymentFailedEvent.orderNumber, kafkaObjectMapper.writeValueAsString(paymentFailedEvent)),
            ).get()

            eventually(30.seconds) {
                val updatedInventory = requireNotNull(inventoryRepository.findBySkuCode("SKU-KT-400"))
                updatedInventory.quantity shouldBeExactly 8
                inventoryReservationRepository.existsById(404L) shouldBe false
            }
        }
```

- [ ] **Step 2: Run the test**

```bash
cd inventory-service && mvn -B test -Dtest=InventorySagaIntegrationTest -Dspring.profiles.active=test
```

Expected: `BUILD SUCCESS`, all tests pass (including the new one).

- [ ] **Step 3: Commit**

```bash
git add inventory-service/src/test/kotlin/com/paulopacifico/inventoryservice/inventory/InventorySagaIntegrationTest.kt
git commit -m "Add inventory compensation test for payment failure saga"
```

---

## Task 9: Create payment-service Maven project structure

**Files:**
- Create: `payment-service/pom.xml`

- [ ] **Step 1: Create pom.xml**

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>

    <groupId>com.paulopacifico</groupId>
    <artifactId>payment-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>payment-service</name>
    <description>Payment Service for an event-driven commerce platform</description>

    <properties>
        <java.version>21</java.version>
        <kotlin.version>2.1.10</kotlin.version>
        <kotest.version>5.9.1</kotest.version>
        <mockk.version>1.13.13</mockk.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-reflect</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.module</groupId>
            <artifactId>jackson-module-kotlin</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-brave</artifactId>
        </dependency>
        <dependency>
            <groupId>io.zipkin.reporter2</groupId>
            <artifactId>zipkin-reporter-brave</artifactId>
        </dependency>

        <!-- Test dependencies -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
            <exclusions>
                <exclusion>
                    <groupId>org.junit.vintage</groupId>
                    <artifactId>junit-vintage-engine</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.kotest</groupId>
            <artifactId>kotest-runner-junit5</artifactId>
            <version>${kotest.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.kotest</groupId>
            <artifactId>kotest-assertions-core</artifactId>
            <version>${kotest.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.kotest.extensions</groupId>
            <artifactId>kotest-extensions-spring</artifactId>
            <version>1.3.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.mockk</groupId>
            <artifactId>mockk</artifactId>
            <version>${mockk.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>${project.basedir}/src/main/kotlin</sourceDirectory>
        <testSourceDirectory>${project.basedir}/src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>${kotlin.version}</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <phase>compile</phase>
                        <goals><goal>compile</goal></goals>
                    </execution>
                    <execution>
                        <id>test-compile</id>
                        <phase>test-compile</phase>
                        <goals><goal>test-compile</goal></goals>
                    </execution>
                </executions>
                <configuration>
                    <jvmTarget>21</jvmTarget>
                    <compilerPlugins>
                        <plugin>spring</plugin>
                        <plugin>jpa</plugin>
                    </compilerPlugins>
                    <args>
                        <arg>-Xjsr305=strict</arg>
                    </args>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-allopen</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-noarg</artifactId>
                        <version>${kotlin.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <useModulePath>false</useModulePath>
                </configuration>
            </plugin>
        </plugins>
    </build>
    <profiles>
        <profile>
            <id>skip-integration-tests</id>
            <activation>
                <property><name>skipIntegrationTests</name></property>
            </activation>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-surefire-plugin</artifactId>
                        <configuration>
                            <excludes>
                                <exclude>**/*IntegrationTest.kt</exclude>
                                <exclude>**/*IntegrationTest</exclude>
                            </excludes>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 2: Create the directory tree**

```bash
mkdir -p payment-service/src/main/kotlin/com/paulopacifico/paymentservice
mkdir -p payment-service/src/main/kotlin/com/paulopacifico/paymentservice/messaging/api
mkdir -p payment-service/src/main/kotlin/com/paulopacifico/paymentservice/messaging/config
mkdir -p payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/domain
mkdir -p payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/application
mkdir -p payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/messaging/persistence
mkdir -p payment-service/src/main/resources/db/migration
mkdir -p payment-service/src/test/kotlin/com/paulopacifico/paymentservice/support
mkdir -p payment-service/src/test/kotlin/com/paulopacifico/paymentservice/payment
mkdir -p payment-service/src/test/resources
```

- [ ] **Step 3: Verify pom.xml parses**

```bash
cd payment-service && mvn -B help:effective-pom -q 2>&1 | tail -5
```

Expected: no error output.

- [ ] **Step 4: Commit**

```bash
git add payment-service/pom.xml
git commit -m "Add payment-service Maven project skeleton"
```

---

## Task 10: Create payment-service core domain and configuration

**Files:**
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/PaymentServiceApplication.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/domain/PaymentEntity.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/application/PaymentRepository.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/messaging/persistence/ProcessedOrderConfirmedEventEntity.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/messaging/persistence/ProcessedOrderConfirmedEventRepository.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/application/PaymentProperties.kt`
- Create: `payment-service/src/main/resources/db/migration/V1__create_payments_table.sql`
- Create: `payment-service/src/main/resources/db/migration/V2__create_processed_order_confirmed_events_table.sql`

- [ ] **Step 1: Create PaymentServiceApplication.kt**

```kotlin
package com.paulopacifico.paymentservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PaymentServiceApplication

fun main(args: Array<String>) {
    runApplication<PaymentServiceApplication>(*args)
}
```

- [ ] **Step 2: Create V1 migration**

```sql
CREATE TABLE payments (
    id           BIGSERIAL                NOT NULL PRIMARY KEY,
    order_id     BIGINT                   NOT NULL UNIQUE,
    order_number VARCHAR(64)              NOT NULL,
    amount       NUMERIC(19, 2)           NOT NULL,
    status       VARCHAR(16)              NOT NULL,
    failure_reason VARCHAR(255),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

- [ ] **Step 3: Create V2 migration**

```sql
CREATE TABLE processed_order_confirmed_events (
    event_id     UUID                     NOT NULL PRIMARY KEY,
    order_id     BIGINT                   NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

- [ ] **Step 4: Create PaymentEntity.kt**

```kotlin
package com.paulopacifico.paymentservice.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "payments")
class PaymentEntity(
    @Column(name = "order_id", nullable = false, unique = true)
    val orderId: Long,

    @Column(name = "order_number", nullable = false, length = 64)
    val orderNumber: String,

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, length = 16)
    var status: String,

    @Column(name = "failure_reason", length = 255)
    var failureReason: String? = null,

    @Column(name = "processed_at", nullable = false)
    val processedAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
```

- [ ] **Step 5: Create PaymentRepository.kt**

```kotlin
package com.paulopacifico.paymentservice.payment.application

import com.paulopacifico.paymentservice.payment.domain.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<PaymentEntity, Long>
```

- [ ] **Step 6: Create ProcessedOrderConfirmedEventEntity.kt**

```kotlin
package com.paulopacifico.paymentservice.payment.messaging.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "processed_order_confirmed_events")
class ProcessedOrderConfirmedEventEntity(
    @Id
    val eventId: UUID,

    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val processedAt: OffsetDateTime,
)
```

- [ ] **Step 7: Create ProcessedOrderConfirmedEventRepository.kt**

```kotlin
package com.paulopacifico.paymentservice.payment.messaging.persistence

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedOrderConfirmedEventRepository : JpaRepository<ProcessedOrderConfirmedEventEntity, UUID>
```

- [ ] **Step 8: Create PaymentProperties.kt**

```kotlin
package com.paulopacifico.paymentservice.payment.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("payment")
data class PaymentProperties(
    val successRate: Double = 1.0,
)
```

- [ ] **Step 9: Commit**

```bash
git add payment-service/src/main/kotlin/com/paulopacifico/paymentservice/PaymentServiceApplication.kt
git add payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/
git add payment-service/src/main/resources/db/migration/
git commit -m "Add payment-service domain, repositories, and Flyway migrations"
```

---

## Task 11: Create payment-service event contracts, Kafka config, and application.yml

**Files:**
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/messaging/api/OrderConfirmedEvent.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/messaging/api/PaymentSucceededEvent.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/messaging/api/PaymentFailedEvent.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/messaging/config/KafkaTopicProperties.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/messaging/config/PaymentKafkaConfiguration.kt`
- Create: `payment-service/src/main/resources/application.yml`

- [ ] **Step 1: Create OrderConfirmedEvent.kt**

```kotlin
package com.paulopacifico.paymentservice.messaging.api

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class OrderConfirmedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val price: BigDecimal,
    val quantity: Int,
    val occurredAt: OffsetDateTime,
)
```

- [ ] **Step 2: Create PaymentSucceededEvent.kt**

```kotlin
package com.paulopacifico.paymentservice.messaging.api

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class PaymentSucceededEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val amount: BigDecimal,
    val occurredAt: OffsetDateTime,
)
```

- [ ] **Step 3: Create PaymentFailedEvent.kt**

```kotlin
package com.paulopacifico.paymentservice.messaging.api

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class PaymentFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val amount: BigDecimal,
    val reason: String,
    val occurredAt: OffsetDateTime,
)
```

- [ ] **Step 4: Create KafkaTopicProperties.kt**

```kotlin
package com.paulopacifico.paymentservice.messaging.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.kafka.topics")
data class KafkaTopicProperties(
    var orderConfirmed: String = "order-confirmed-topic",
    var paymentSucceeded: String = "payment-succeeded-topic",
    var paymentFailed: String = "payment-failed-topic",
)
```

- [ ] **Step 5: Create PaymentKafkaConfiguration.kt**

```kotlin
package com.paulopacifico.paymentservice.messaging.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.support.converter.StringJsonMessageConverter
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.util.backoff.FixedBackOff
import java.util.HashMap

@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaTopicProperties::class)
class PaymentKafkaConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean("kafkaObjectMapper")
    fun kafkaObjectMapper(objectMapperBuilder: Jackson2ObjectMapperBuilder): ObjectMapper =
        objectMapperBuilder
            .createXmlMapper(false)
            .build<ObjectMapper>()
            .registerKotlinModule()

    @Bean
    fun producerFactory(kafkaProperties: KafkaProperties): ProducerFactory<String, String> {
        val properties = HashMap(kafkaProperties.buildProducerProperties())
        properties.putIfAbsent(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        properties.putIfAbsent(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        return DefaultKafkaProducerFactory(properties)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory)

    @Bean
    fun consumerFactory(kafkaProperties: KafkaProperties): ConsumerFactory<String, String> {
        val properties = HashMap(kafkaProperties.buildConsumerProperties())
        properties.putIfAbsent(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        properties.putIfAbsent(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        return DefaultKafkaConsumerFactory(properties)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        @Qualifier("kafkaObjectMapper") kafkaObjectMapper: ObjectMapper,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            val jackson2MessageConverter = MappingJackson2MessageConverter()
            jackson2MessageConverter.objectMapper = kafkaObjectMapper.copy()
            val recordMessageConverter = StringJsonMessageConverter(kafkaObjectMapper.copy())
            recordMessageConverter.setMessagingConverter(jackson2MessageConverter)
            setRecordMessageConverter(recordMessageConverter)
            setCommonErrorHandler(kafkaErrorHandler())
        }

    @Bean
    fun kafkaErrorHandler(): DefaultErrorHandler =
        DefaultErrorHandler(
            { record, exception ->
                logger.error(
                    "Skipping Kafka record topic={} partition={} offset={} due to {}",
                    record.topic(), record.partition(), record.offset(), exception.message, exception,
                )
            },
            FixedBackOff(1_000L, 5L),
        ).apply {
            setRetryListeners(
                RetryListener { record, exception, deliveryAttempt ->
                    logger.warn(
                        "Retrying Kafka record topic={} partition={} offset={} attempt={} due to {}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt, exception.message,
                    )
                },
            )
        }

    @Bean
    fun orderConfirmedTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.orderConfirmed).partitions(3).replicas(1).build()

    @Bean
    fun paymentSucceededTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.paymentSucceeded).partitions(3).replicas(1).build()

    @Bean
    fun paymentFailedTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.paymentFailed).partitions(3).replicas(1).build()
}
```

- [ ] **Step 6: Create application.yml**

```yaml
spring:
  application:
    name: payment-service
  datasource:
    url: ${PAYMENT_DB_URL:jdbc:postgresql://localhost:5432/payment_db}
    username: ${PAYMENT_DB_USERNAME:oms}
    password: ${PAYMENT_DB_PASSWORD:oms}
    driver-class-name: org.postgresql.Driver
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  jackson:
    time-zone: UTC
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 1
    consumer:
      group-id: ${KAFKA_CONSUMER_GROUP:payment-service}
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      properties:
        isolation.level: read_committed
    listener:
      ack-mode: record
      missing-topics-fatal: false
      observation-enabled: true
    template:
      observation-enabled: true

server:
  port: ${SERVER_PORT:8083}

management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}

app:
  kafka:
    topics:
      order-confirmed: ${ORDER_CONFIRMED_TOPIC:order-confirmed-topic}
      payment-succeeded: ${PAYMENT_SUCCEEDED_TOPIC:payment-succeeded-topic}
      payment-failed: ${PAYMENT_FAILED_TOPIC:payment-failed-topic}

payment:
  success-rate: ${PAYMENT_SUCCESS_RATE:1.0}

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [traceId=%X{traceId:-}, spanId=%X{spanId:-}] %-5level [%t] %logger{50} : %m%n"
  level:
    root: INFO
    com.paulopacifico.paymentservice: INFO
```

- [ ] **Step 7: Verify compilation**

```bash
cd payment-service && mvn -B compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
git add payment-service/src/main/kotlin/com/paulopacifico/paymentservice/messaging/
git add payment-service/src/main/resources/application.yml
git commit -m "Add payment-service event contracts, Kafka config, and application properties"
```

---

## Task 12: Create PaymentService, OrderConfirmedSagaConsumer, and PaymentSagaEventPublisher

**Files:**
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/application/PaymentService.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/messaging/OrderConfirmedSagaConsumer.kt`
- Create: `payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/messaging/PaymentSagaEventPublisher.kt`

- [ ] **Step 1: Create PaymentService.kt**

```kotlin
package com.paulopacifico.paymentservice.payment.application

import com.paulopacifico.paymentservice.payment.domain.PaymentEntity
import com.paulopacifico.paymentservice.messaging.api.OrderConfirmedEvent
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service

@Service
@EnableConfigurationProperties(PaymentProperties::class)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentProperties: PaymentProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Process a payment for the given confirmed order.
     * Returns the persisted PaymentEntity with status SUCCEEDED or FAILED.
     */
    fun processPayment(event: OrderConfirmedEvent): PaymentEntity {
        val amount = event.price.multiply(java.math.BigDecimal(event.quantity))
        val succeeded = Math.random() < paymentProperties.successRate

        val payment = if (succeeded) {
            logger.info("Payment succeeded for orderId={} orderNumber={} amount={}", event.orderId, event.orderNumber, amount)
            PaymentEntity(
                orderId = event.orderId,
                orderNumber = event.orderNumber,
                amount = amount,
                status = "SUCCEEDED",
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
        } else {
            val reason = "Payment declined by payment provider"
            logger.info("Payment failed for orderId={} orderNumber={} reason={}", event.orderId, event.orderNumber, reason)
            PaymentEntity(
                orderId = event.orderId,
                orderNumber = event.orderNumber,
                amount = amount,
                status = "FAILED",
                failureReason = reason,
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
        }

        return paymentRepository.save(payment)
    }
}
```

- [ ] **Step 2: Create PaymentSagaEventPublisher.kt**

```kotlin
package com.paulopacifico.paymentservice.payment.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.paulopacifico.paymentservice.messaging.api.PaymentFailedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentSucceededEvent
import com.paulopacifico.paymentservice.messaging.config.KafkaTopicProperties
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PaymentSagaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("kafkaObjectMapper")
    private val kafkaObjectMapper: ObjectMapper,
    private val topics: KafkaTopicProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val sendTimeout: Duration = Duration.ofSeconds(10)

    fun publishSucceeded(event: PaymentSucceededEvent) {
        publish(
            topic = topics.paymentSucceeded,
            key = event.orderNumber,
            event = event,
            eventName = "PaymentSucceededEvent",
        )
    }

    fun publishFailed(event: PaymentFailedEvent) {
        publish(
            topic = topics.paymentFailed,
            key = event.orderNumber,
            event = event,
            eventName = "PaymentFailedEvent",
        )
    }

    private fun publish(topic: String, key: String, event: Any, eventName: String) {
        try {
            val payload = kafkaObjectMapper.writeValueAsString(event)
            kafkaTemplate.send(topic, key, payload).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
            logger.info("Published {} topic={} key={}", eventName, topic, key)
        } catch (exception: JsonProcessingException) {
            throw IllegalStateException("Failed to serialize $eventName for key=$key", exception)
        } catch (exception: Exception) {
            throw IllegalStateException("Failed to publish $eventName for key=$key", exception)
        }
    }
}
```

- [ ] **Step 3: Create OrderConfirmedSagaConsumer.kt**

```kotlin
package com.paulopacifico.paymentservice.payment.messaging

import com.paulopacifico.paymentservice.messaging.api.OrderConfirmedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentFailedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentSucceededEvent
import com.paulopacifico.paymentservice.payment.application.PaymentService
import com.paulopacifico.paymentservice.payment.messaging.persistence.ProcessedOrderConfirmedEventEntity
import com.paulopacifico.paymentservice.payment.messaging.persistence.ProcessedOrderConfirmedEventRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderConfirmedSagaConsumer(
    private val paymentService: PaymentService,
    private val paymentSagaEventPublisher: PaymentSagaEventPublisher,
    private val processedOrderConfirmedEventRepository: ProcessedOrderConfirmedEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @KafkaListener(
        id = "orderConfirmedSagaConsumer",
        topics = ["\${app.kafka.topics.order-confirmed}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumeOrderConfirmed(event: OrderConfirmedEvent) {
        if (processedOrderConfirmedEventRepository.existsById(event.eventId)) {
            logger.info("Ignoring duplicate OrderConfirmedEvent eventId={} orderId={}", event.eventId, event.orderId)
            return
        }

        val payment = paymentService.processPayment(event)

        if (payment.status == "SUCCEEDED") {
            paymentSagaEventPublisher.publishSucceeded(
                PaymentSucceededEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    amount = payment.amount,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        } else {
            paymentSagaEventPublisher.publishFailed(
                PaymentFailedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    amount = payment.amount,
                    reason = payment.failureReason ?: "Unknown payment failure",
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        }

        processedOrderConfirmedEventRepository.save(
            ProcessedOrderConfirmedEventEntity(
                eventId = event.eventId,
                orderId = event.orderId,
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
        logger.info("Processed OrderConfirmedEvent eventId={} orderId={} paymentStatus={}", event.eventId, event.orderId, payment.status)
    }
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd payment-service && mvn -B compile -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/application/PaymentService.kt
git add payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/messaging/PaymentSagaEventPublisher.kt
git add payment-service/src/main/kotlin/com/paulopacifico/paymentservice/payment/messaging/OrderConfirmedSagaConsumer.kt
git commit -m "Add PaymentService, OrderConfirmedSagaConsumer, and PaymentSagaEventPublisher"
```

---

## Task 13: Create payment-service integration tests

**Files:**
- Create: `payment-service/src/test/kotlin/com/paulopacifico/paymentservice/support/AbstractIntegrationTest.kt`
- Create: `payment-service/src/test/resources/application-test.yml`
- Create: `payment-service/src/test/kotlin/com/paulopacifico/paymentservice/payment/PaymentSagaIntegrationTest.kt`

- [ ] **Step 1: Create AbstractIntegrationTest.kt**

```kotlin
package com.paulopacifico.paymentservice.support

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import java.util.stream.Stream
import kotlin.time.Duration.Companion.seconds

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
abstract class AbstractIntegrationTest(body: StringSpec.() -> Unit = {}) : StringSpec(body) {

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    override fun extensions() = listOf(SpringExtension)

    protected fun kafkaConsumer(groupId: String): Consumer<String, String> =
        KafkaConsumer<String, String>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "$groupId-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ),
        )

    protected suspend fun awaitTopicReady(vararg topicNames: String) {
        AdminClient.create(
            mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers),
        ).use { adminClient ->
            eventually(20.seconds) {
                val existingTopics = adminClient.listTopics().names().get()
                existingTopics shouldContainAll topicNames.toList()
                val topicDescriptions = adminClient.describeTopics(topicNames.toList()).allTopicNames().get()
                topicDescriptions.values.forEach { description ->
                    description.partitions().size shouldBe 3
                }
            }
        }
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("test")
            .withPassword("test")

        @Container
        @ServiceConnection
        @JvmStatic
        val kafka = KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"),
        )

        init {
            Startables.deepStart(Stream.of(postgres, kafka)).join()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
            registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers)
            registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers)
        }
    }
}
```

- [ ] **Step 2: Create application-test.yml**

```yaml
payment:
  success-rate: 1.0
```

- [ ] **Step 3: Create PaymentSagaIntegrationTest.kt**

```kotlin
package com.paulopacifico.paymentservice.payment

import com.paulopacifico.paymentservice.messaging.api.OrderConfirmedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentFailedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentSucceededEvent
import com.paulopacifico.paymentservice.payment.application.PaymentRepository
import com.paulopacifico.paymentservice.payment.messaging.persistence.ProcessedOrderConfirmedEventRepository
import com.paulopacifico.paymentservice.support.AbstractIntegrationTest
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeExactly
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["payment.success-rate=1.0"])
class PaymentSagaIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var processedOrderConfirmedEventRepository: ProcessedOrderConfirmedEventRepository

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry

    @Autowired
    @Qualifier("kafkaObjectMapper")
    lateinit var kafkaObjectMapper: com.fasterxml.jackson.databind.ObjectMapper

    init {
        beforeTest {
            paymentRepository.deleteAll()
            processedOrderConfirmedEventRepository.deleteAll()
        }

        "should process payment and publish PaymentSucceededEvent when success rate is 1.0" {
            awaitTopicReady("order-confirmed-topic", "payment-succeeded-topic", "payment-failed-topic")

            kafkaConsumer("payment-service-it").use { consumer ->
                consumer.subscribe(listOf("payment-succeeded-topic", "payment-failed-topic"))
                eventually(30.seconds) {
                    consumer.poll(Duration.ofMillis(200))
                    consumer.assignment().size shouldBeExactly 6
                }
                consumer.seekToEnd(consumer.assignment())

                val listenerContainer = requireNotNull(
                    kafkaListenerEndpointRegistry.getListenerContainer("orderConfirmedSagaConsumer"),
                ) { "Kafka listener container orderConfirmedSagaConsumer was not registered" }
                ContainerTestUtils.waitForAssignment(listenerContainer, 3)

                val event = OrderConfirmedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = 501L,
                    orderNumber = "ORD-KT-501",
                    price = BigDecimal("25.00"),
                    quantity = 2,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                )

                kafkaTemplate.send(
                    ProducerRecord(
                        "order-confirmed-topic",
                        event.orderNumber,
                        kafkaObjectMapper.writeValueAsString(event),
                    ),
                ).get()

                val collectedRecords = mutableListOf<ConsumerRecord<String, String>>()

                eventually(30.seconds) {
                    consumer.poll(Duration.ofMillis(500)).forEach { collectedRecords.add(it) }

                    val payment = paymentRepository.findAll().firstOrNull { it.orderId == 501L }
                    requireNotNull(payment) { "Expected payment record for orderId=501 but none found" }
                    payment.status shouldBe "SUCCEEDED"
                    payment.amount shouldBe BigDecimal("50.00")

                    val succeededRecord = requireNotNull(
                        collectedRecords.firstOrNull { it.topic() == "payment-succeeded-topic" }
                    ) { "Expected payment-succeeded-topic record but none published" }
                    val succeededEvent = kafkaObjectMapper.readValue(succeededRecord.value(), PaymentSucceededEvent::class.java)
                    succeededEvent.orderId shouldBe 501L
                    succeededEvent.orderNumber shouldBe "ORD-KT-501"
                    succeededEvent.amount shouldBe BigDecimal("50.00")
                }
            }
        }

        "should publish PaymentFailedEvent and persist failure record when success rate is 0.0" {
            awaitTopicReady("order-confirmed-topic", "payment-succeeded-topic", "payment-failed-topic")

            // Override success rate to 0.0 for this test via direct bean manipulation would require
            // a separate test class with @TestPropertySource. Instead, inject PaymentService and use reflection,
            // or use a separate nested class. The simplest approach: use a unique orderId and verify FAILED status.
            // Since the success-rate is set to 1.0 in @TestPropertySource above, we need a separate class.
            // This test must live in PaymentSagaFailureIntegrationTest (Task 13b below).
            // Skip this case here — it is covered by the separate failure test class.
        }

        "should be idempotent when same OrderConfirmedEvent is delivered twice" {
            awaitTopicReady("order-confirmed-topic")

            val listenerContainer = requireNotNull(
                kafkaListenerEndpointRegistry.getListenerContainer("orderConfirmedSagaConsumer"),
            ) { "Kafka listener container orderConfirmedSagaConsumer was not registered" }
            ContainerTestUtils.waitForAssignment(listenerContainer, 3)

            val eventId = UUID.randomUUID()
            val event = OrderConfirmedEvent(
                eventId = eventId,
                orderId = 601L,
                orderNumber = "ORD-KT-601",
                price = BigDecimal("10.00"),
                quantity = 1,
                occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
            val payload = kafkaObjectMapper.writeValueAsString(event)

            // Send the same event twice
            kafkaTemplate.send(ProducerRecord("order-confirmed-topic", event.orderNumber, payload)).get()
            kafkaTemplate.send(ProducerRecord("order-confirmed-topic", event.orderNumber, payload)).get()

            eventually(30.seconds) {
                val payments = paymentRepository.findAll().filter { it.orderId == 601L }
                payments.size shouldBeExactly 1
            }
        }
    }
}
```

- [ ] **Step 4: Create PaymentSagaFailureIntegrationTest.kt** (separate class to override success-rate to 0.0)

```kotlin
package com.paulopacifico.paymentservice.payment

import com.paulopacifico.paymentservice.messaging.api.OrderConfirmedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentFailedEvent
import com.paulopacifico.paymentservice.payment.application.PaymentRepository
import com.paulopacifico.paymentservice.payment.messaging.persistence.ProcessedOrderConfirmedEventRepository
import com.paulopacifico.paymentservice.support.AbstractIntegrationTest
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.kotest.matchers.ints.shouldBeExactly
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["payment.success-rate=0.0"])
class PaymentSagaFailureIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var processedOrderConfirmedEventRepository: ProcessedOrderConfirmedEventRepository

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry

    @Autowired
    @Qualifier("kafkaObjectMapper")
    lateinit var kafkaObjectMapper: com.fasterxml.jackson.databind.ObjectMapper

    init {
        beforeTest {
            paymentRepository.deleteAll()
            processedOrderConfirmedEventRepository.deleteAll()
        }

        "should publish PaymentFailedEvent and persist failure record when success rate is 0.0" {
            awaitTopicReady("order-confirmed-topic", "payment-succeeded-topic", "payment-failed-topic")

            kafkaConsumer("payment-service-failure-it").use { consumer ->
                consumer.subscribe(listOf("payment-succeeded-topic", "payment-failed-topic"))
                eventually(30.seconds) {
                    consumer.poll(Duration.ofMillis(200))
                    consumer.assignment().size shouldBeExactly 6
                }
                consumer.seekToEnd(consumer.assignment())

                val listenerContainer = requireNotNull(
                    kafkaListenerEndpointRegistry.getListenerContainer("orderConfirmedSagaConsumer"),
                ) { "Kafka listener container orderConfirmedSagaConsumer was not registered" }
                ContainerTestUtils.waitForAssignment(listenerContainer, 3)

                val event = OrderConfirmedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = 701L,
                    orderNumber = "ORD-KT-701",
                    price = BigDecimal("30.00"),
                    quantity = 3,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                )

                kafkaTemplate.send(
                    ProducerRecord(
                        "order-confirmed-topic",
                        event.orderNumber,
                        kafkaObjectMapper.writeValueAsString(event),
                    ),
                ).get()

                val collectedRecords = mutableListOf<ConsumerRecord<String, String>>()

                eventually(30.seconds) {
                    consumer.poll(Duration.ofMillis(500)).forEach { collectedRecords.add(it) }

                    val payment = paymentRepository.findAll().firstOrNull { it.orderId == 701L }
                    requireNotNull(payment) { "Expected payment record for orderId=701 but none found" }
                    payment.status shouldBe "FAILED"
                    payment.failureReason shouldBe "Payment declined by payment provider"

                    val failedRecord = requireNotNull(
                        collectedRecords.firstOrNull { it.topic() == "payment-failed-topic" }
                    ) { "Expected payment-failed-topic record but none published" }
                    val failedEvent = kafkaObjectMapper.readValue(failedRecord.value(), PaymentFailedEvent::class.java)
                    failedEvent.orderId shouldBe 701L
                    failedEvent.reason shouldBe "Payment declined by payment provider"
                }
            }
        }
    }
}
```

- [ ] **Step 5: Run the integration tests**

```bash
cd payment-service && mvn -B test -Dspring.profiles.active=test
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add payment-service/src/test/kotlin/com/paulopacifico/paymentservice/support/AbstractIntegrationTest.kt
git add payment-service/src/test/resources/application-test.yml
git add payment-service/src/test/kotlin/com/paulopacifico/paymentservice/payment/PaymentSagaIntegrationTest.kt
git add payment-service/src/test/kotlin/com/paulopacifico/paymentservice/payment/PaymentSagaFailureIntegrationTest.kt
git commit -m "Add payment-service integration tests for success, failure, and idempotency"
```

---

## Task 14: Create Dockerfile for payment-service

**Files:**
- Create: `payment-service/Dockerfile`

- [ ] **Step 1: Create Dockerfile**

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/payment-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 2: Verify Docker build**

```bash
cd payment-service && docker build -t payment-service:ci .
```

Expected: image builds successfully.

- [ ] **Step 3: Commit**

```bash
git add payment-service/Dockerfile
git commit -m "Add payment-service Dockerfile"
```

---

## Task 15: Add payment_db to Docker Compose infrastructure

**Files:**
- Modify: `docker/postgres/init/01-create-databases.sql`

- [ ] **Step 1: Add payment_db**

```sql
CREATE DATABASE order_db;
CREATE DATABASE inventory_db;
CREATE DATABASE payment_db;
```

- [ ] **Step 2: Commit**

```bash
git add docker/postgres/init/01-create-databases.sql
git commit -m "Add payment_db to Postgres init script"
```

---

## Task 16: Create Helm chart for payment-service

**Files:** All new under `helm/payment-service/`

- [ ] **Step 1: Create Chart.yaml**

```yaml
apiVersion: v2
name: payment-service
description: Payment Service for the distributed commerce platform — handles payment processing and saga compensation
type: application
version: 0.1.0
appVersion: "0.0.1-SNAPSHOT"
```

- [ ] **Step 2: Create values.yaml**

```yaml
image:
  repository: payment-service
  tag: latest
  pullPolicy: IfNotPresent

replicaCount: 1

service:
  type: ClusterIP
  port: 8083

resources:
  requests:
    cpu: 250m
    memory: 384Mi
  limits:
    cpu: 500m
    memory: 768Mi

config:
  serverPort: "8083"
  kafkaBootstrapServers: "kafka:9092"
  kafkaConsumerGroup: "payment-service"
  zipkinEndpoint: "http://zipkin:9411/api/v2/spans"
  dbUrl: "jdbc:postgresql://postgres:5432/payment_db"
  paymentSuccessRate: "1.0"
  topics:
    orderConfirmed: "order-confirmed-topic"
    paymentSucceeded: "payment-succeeded-topic"
    paymentFailed: "payment-failed-topic"

# Sensitive values — never commit real credentials.
# Supply at deploy time via: --set secret.dbUsername=... --set secret.dbPassword=...
secret:
  dbUsername: ""
  dbPassword: ""

hpa:
  enabled: false
  minReplicas: 1
  maxReplicas: 3
  targetCPUUtilizationPercentage: 70

probes:
  liveness:
    path: /actuator/health
    initialDelaySeconds: 60
    periodSeconds: 15
    failureThreshold: 3
  readiness:
    path: /actuator/health
    initialDelaySeconds: 30
    periodSeconds: 10
    failureThreshold: 3
```

- [ ] **Step 3: Create values-dev.yaml**

```yaml
resources:
  requests:
    cpu: 100m
    memory: 256Mi
  limits:
    cpu: 500m
    memory: 512Mi
```

- [ ] **Step 4: Create values-prod.yaml**

```yaml
replicaCount: 2

image:
  pullPolicy: Always

resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    cpu: 1000m
    memory: 1Gi

config:
  kafkaBootstrapServers: "${KAFKA_BOOTSTRAP_SERVERS}"
  dbUrl: "${PAYMENT_DB_URL}"
  zipkinEndpoint: "${ZIPKIN_ENDPOINT}"

hpa:
  enabled: true
  minReplicas: 2
  maxReplicas: 5
  targetCPUUtilizationPercentage: 70
```

- [ ] **Step 5: Create templates/_helpers.tpl**

```
{{/*
Fully qualified app name: <release>-<chart>, truncated to 63 chars.
*/}}
{{- define "payment-service.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource.
*/}}
{{- define "payment-service.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels used by Deployment and Service to match pods.
*/}}
{{- define "payment-service.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
```

- [ ] **Step 6: Create templates/configmap.yaml**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "payment-service.fullname" . }}
  labels:
    {{- include "payment-service.labels" . | nindent 4 }}
data:
  SERVER_PORT: {{ .Values.config.serverPort | quote }}
  KAFKA_BOOTSTRAP_SERVERS: {{ .Values.config.kafkaBootstrapServers | quote }}
  KAFKA_CONSUMER_GROUP: {{ .Values.config.kafkaConsumerGroup | quote }}
  ZIPKIN_ENDPOINT: {{ .Values.config.zipkinEndpoint | quote }}
  PAYMENT_DB_URL: {{ .Values.config.dbUrl | quote }}
  PAYMENT_SUCCESS_RATE: {{ .Values.config.paymentSuccessRate | quote }}
  ORDER_CONFIRMED_TOPIC: {{ .Values.config.topics.orderConfirmed | quote }}
  PAYMENT_SUCCEEDED_TOPIC: {{ .Values.config.topics.paymentSucceeded | quote }}
  PAYMENT_FAILED_TOPIC: {{ .Values.config.topics.paymentFailed | quote }}
```

- [ ] **Step 7: Create templates/secret.yaml**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "payment-service.fullname" . }}
  labels:
    {{- include "payment-service.labels" . | nindent 4 }}
type: Opaque
data:
  PAYMENT_DB_USERNAME: {{ .Values.secret.dbUsername | b64enc | quote }}
  PAYMENT_DB_PASSWORD: {{ .Values.secret.dbPassword | b64enc | quote }}
```

- [ ] **Step 8: Create templates/deployment.yaml**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "payment-service.fullname" . }}
  labels:
    {{- include "payment-service.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      {{- include "payment-service.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "payment-service.selectorLabels" . | nindent 8 }}
    spec:
      containers:
        - name: {{ .Chart.Name }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - containerPort: {{ .Values.service.port }}
              protocol: TCP
          envFrom:
            - configMapRef:
                name: {{ include "payment-service.fullname" . }}
            - secretRef:
                name: {{ include "payment-service.fullname" . }}
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.liveness.path }}
              port: {{ .Values.service.port }}
            initialDelaySeconds: {{ .Values.probes.liveness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
            failureThreshold: {{ .Values.probes.liveness.failureThreshold }}
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.readiness.path }}
              port: {{ .Values.service.port }}
            initialDelaySeconds: {{ .Values.probes.readiness.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
            failureThreshold: {{ .Values.probes.readiness.failureThreshold }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

- [ ] **Step 9: Create templates/service.yaml**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ include "payment-service.fullname" . }}
  labels:
    {{- include "payment-service.labels" . | nindent 4 }}
spec:
  type: {{ .Values.service.type }}
  selector:
    {{- include "payment-service.selectorLabels" . | nindent 4 }}
  ports:
    - port: {{ .Values.service.port }}
      targetPort: {{ .Values.service.port }}
      protocol: TCP
```

- [ ] **Step 10: Create templates/hpa.yaml**

```yaml
{{- if .Values.hpa.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "payment-service.fullname" . }}
  labels:
    {{- include "payment-service.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "payment-service.fullname" . }}
  minReplicas: {{ .Values.hpa.minReplicas }}
  maxReplicas: {{ .Values.hpa.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.hpa.targetCPUUtilizationPercentage }}
{{- end }}
```

- [ ] **Step 11: Lint the chart locally**

```bash
helm lint helm/payment-service -f helm/payment-service/values-dev.yaml
helm lint helm/payment-service -f helm/payment-service/values-prod.yaml
```

Expected: `0 chart(s) failed` for both.

- [ ] **Step 12: Template dry-run**

```bash
helm template payment-service helm/payment-service -f helm/payment-service/values-dev.yaml --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
helm template payment-service helm/payment-service -f helm/payment-service/values-prod.yaml --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
```

Expected: no errors.

- [ ] **Step 13: Commit**

```bash
git add helm/payment-service/
git commit -m "Add Helm chart for payment-service"
```

---

## Task 17: Update CI/CD pipeline for payment-service

**Files:**
- Modify: `.github/workflows/ci-cd.yml`

- [ ] **Step 1: Add the payment-service steps**

Replace the CI file content with the following (adds pre-pull, build/test, Docker build, Helm lint/template for payment-service):

```yaml
name: Polyglot CI/CD

on:
  push:
    branches:
      - main
      - develop
  pull_request:
    branches:
      - main

jobs:
  build-test-and-package:
    runs-on: ubuntu-latest
    env:
      TESTCONTAINERS_CHECKS_DISABLE: true
      DOCKER_HOST: unix:///var/run/docker.sock

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Verify Docker is available
        run: |
          docker info
          docker version
          echo "DOCKER_HOST=${DOCKER_HOST}"

      - name: Pull Testcontainers images
        run: |
          docker pull postgres:16-alpine
          docker pull apache/kafka-native:3.8.0

      - name: Build and test order-service
        working-directory: order-service
        run: mvn -B clean verify -Dspring.profiles.active=test

      - name: Build and test inventory-service
        working-directory: inventory-service
        run: mvn -B clean verify -Dspring.profiles.active=test

      - name: Build and test payment-service
        working-directory: payment-service
        run: mvn -B clean verify -Dspring.profiles.active=test

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: |
            order-service/target/surefire-reports/
            inventory-service/target/surefire-reports/
            payment-service/target/surefire-reports/

      - name: Build Docker image for order-service
        run: docker build -t order-service:ci ./order-service

      - name: Build Docker image for inventory-service
        run: docker build -t inventory-service:ci ./inventory-service

      - name: Build Docker image for payment-service
        run: docker build -t payment-service:ci ./payment-service

      - name: Set up Helm
        uses: azure/setup-helm@v4
        with:
          version: v3.17.0

      - name: Lint Helm charts
        run: |
          helm lint helm/order-service     -f helm/order-service/values-dev.yaml
          helm lint helm/order-service     -f helm/order-service/values-prod.yaml
          helm lint helm/inventory-service -f helm/inventory-service/values-dev.yaml
          helm lint helm/inventory-service -f helm/inventory-service/values-prod.yaml
          helm lint helm/payment-service   -f helm/payment-service/values-dev.yaml
          helm lint helm/payment-service   -f helm/payment-service/values-prod.yaml

      - name: Template Helm charts (dry-run render)
        run: |
          helm template order-service     helm/order-service     -f helm/order-service/values-dev.yaml     --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
          helm template order-service     helm/order-service     -f helm/order-service/values-prod.yaml    --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
          helm template inventory-service helm/inventory-service -f helm/inventory-service/values-dev.yaml  --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
          helm template inventory-service helm/inventory-service -f helm/inventory-service/values-prod.yaml --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
          helm template payment-service   helm/payment-service   -f helm/payment-service/values-dev.yaml   --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
          helm template payment-service   helm/payment-service   -f helm/payment-service/values-prod.yaml  --set secret.dbUsername=ci --set secret.dbPassword=ci > /dev/null
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/ci-cd.yml
git commit -m "Extend CI/CD pipeline to build, test, and validate payment-service"
```

---

## Task 18: Full integration verification

- [ ] **Step 1: Run all service tests locally**

```bash
cd order-service && mvn -B clean verify -Dspring.profiles.active=test && cd ..
cd inventory-service && mvn -B clean verify -Dspring.profiles.active=test && cd ..
cd payment-service && mvn -B clean verify -Dspring.profiles.active=test && cd ..
```

Expected: `BUILD SUCCESS` for all three.

- [ ] **Step 2: Helm lint all charts**

```bash
helm lint helm/order-service     -f helm/order-service/values-dev.yaml
helm lint helm/order-service     -f helm/order-service/values-prod.yaml
helm lint helm/inventory-service -f helm/inventory-service/values-dev.yaml
helm lint helm/inventory-service -f helm/inventory-service/values-prod.yaml
helm lint helm/payment-service   -f helm/payment-service/values-dev.yaml
helm lint helm/payment-service   -f helm/payment-service/values-prod.yaml
```

Expected: `0 chart(s) failed` for all six.

- [ ] **Step 3: Push to main and verify CI passes**

```bash
git push origin main
```

Then watch the GitHub Actions run complete green.
