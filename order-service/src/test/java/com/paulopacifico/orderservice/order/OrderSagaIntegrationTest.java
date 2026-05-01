package com.paulopacifico.orderservice.order;

import com.paulopacifico.orderservice.messaging.api.InventoryFailedEvent;
import com.paulopacifico.orderservice.messaging.api.InventoryReservedEvent;
import com.paulopacifico.orderservice.messaging.api.OrderFailedEvent;
import com.paulopacifico.orderservice.messaging.api.OrderShipmentFailedEvent;
import com.paulopacifico.orderservice.messaging.api.PaymentFailedEvent;
import com.paulopacifico.orderservice.messaging.api.PaymentSucceededEvent;
import com.paulopacifico.orderservice.messaging.api.ShipmentFailedEvent;
import com.paulopacifico.orderservice.order.api.CreateOrderRequest;
import com.paulopacifico.orderservice.order.api.OrderResponse;
import com.paulopacifico.orderservice.order.application.OrderRepository;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import com.paulopacifico.orderservice.support.AbstractIntegrationTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import org.apache.kafka.common.TopicPartition;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderSagaIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldMarkOrderAsPaidWhenPaymentSucceeds() throws Exception {
        // Create an order via HTTP
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
        kafkaTemplate.send(
                "payment-succeeded-topic",
                orderNumber,
                objectMapper.writeValueAsString(event)
        ).get();

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
        // Create an order via HTTP
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
        kafkaTemplate.send(
                "payment-failed-topic",
                orderNumber,
                objectMapper.writeValueAsString(event)
        ).get();

        // Assert order transitions to PAYMENT_FAILED
        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> {
                    var updated = orderRepository.findById(orderId).orElseThrow();
                    assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
                });
    }

    @Test
    void shouldConfirmOrderWhenInventoryIsReserved() throws Exception {
        var order = createPendingOrder("SKU-SAGA-CONFIRM", 3);

        var reservedEvent = new InventoryReservedEvent(
                UUID.randomUUID(),
                order.id(),
                order.orderNumber(),
                order.skuCode(),
                order.quantity(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        kafkaTemplate.send("inventory-reserved-topic", order.orderNumber(), objectMapper.writeValueAsString(reservedEvent)).get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    var updated = getOrder(order.id());
                    assertThat(updated.status()).isEqualTo(OrderStatus.CONFIRMED);
                });
    }

    @Test
    void shouldFailOrderAndPublishCompensationEventWhenInventoryFails() throws Exception {
        var order = createPendingOrder("SKU-SAGA-FAIL", 5);

        var failedEvent = new InventoryFailedEvent(
                UUID.randomUUID(),
                order.id(),
                order.orderNumber(),
                order.skuCode(),
                order.quantity(),
                "Insufficient stock",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        kafkaTemplate.send("inventory-failed-topic", order.orderNumber(), objectMapper.writeValueAsString(failedEvent)).get();

        // Wait for the order to reach FAILED status first. failOrder() publishes the
        // OrderFailedEvent synchronously (kafkaTemplate.send().get()), so once the status
        // is FAILED the compensation event is guaranteed to already be in order-failed-topic.
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(getOrder(order.id()).status()).isEqualTo(OrderStatus.FAILED));

        // Use manual partition assignment and seek(tp, 0) to read from the beginning.
        // seek(tp, offset) sets the fetch position DIRECTLY in the subscription state —
        // no ListOffsets network round-trip, no lazy materialization, no race conditions.
        // This is categorically more reliable than seekToBeginning() for reading a record
        // that is known to already exist in the topic.
        var partitions = List.of(
                new TopicPartition("order-failed-topic", 0),
                new TopicPartition("order-failed-topic", 1),
                new TopicPartition("order-failed-topic", 2)
        );
        try (var consumer = kafkaConsumer("order-saga-it")) {
            consumer.assign(partitions);
            for (var tp : partitions) {
                consumer.seek(tp, 0L);
            }

            var collectedRecords = new ArrayList<String>();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(500)).forEach(r -> collectedRecords.add(r.value()));

                        assertThat(collectedRecords)
                                .withFailMessage("No records received from order-failed-topic")
                                .isNotEmpty();

                        var compensationEvent = collectedRecords.stream()
                                .map(v -> {
                                    try {
                                        return objectMapper.readValue(v, OrderFailedEvent.class);
                                    } catch (Exception e) {
                                        throw new AssertionError("Failed to deserialize OrderFailedEvent: " + v, e);
                                    }
                                })
                                .filter(e -> order.id().equals(e.orderId()))
                                .findFirst();

                        assertThat(compensationEvent).isPresent();
                        assertThat(compensationEvent.get().orderNumber()).isEqualTo(order.orderNumber());
                        assertThat(compensationEvent.get().skuCode()).isEqualTo(order.skuCode());
                        assertThat(compensationEvent.get().reservedQuantity()).isEqualTo(order.quantity());
                        assertThat(compensationEvent.get().reason()).isEqualTo("Insufficient stock");
                    });
        }
    }

    @Test
    void shouldPublishCompensationEventWhenShipmentFails() throws Exception {
        var orderResponse = createPendingOrder("SKU-SHIP-FAIL", 2);

        var orderEntity = orderRepository.findById(orderResponse.id()).orElseThrow();
        orderEntity.confirm();
        orderEntity.pay();
        orderRepository.save(orderEntity);

        var shipmentFailedEvent = new ShipmentFailedEvent(
                UUID.randomUUID(),
                orderResponse.id(),
                orderResponse.orderNumber(),
                "Carrier rejected",
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        kafkaTemplate.send(
                "shipment-failed-topic",
                orderResponse.orderNumber(),
                objectMapper.writeValueAsString(shipmentFailedEvent)
        ).get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .untilAsserted(() ->
                        assertThat(getOrder(orderResponse.id()).status()).isEqualTo(OrderStatus.SHIPMENT_FAILED)
                );

        var partitions = List.of(
                new TopicPartition("order-shipment-failed-topic", 0),
                new TopicPartition("order-shipment-failed-topic", 1),
                new TopicPartition("order-shipment-failed-topic", 2)
        );
        try (var consumer = kafkaConsumer("shipment-saga-it")) {
            consumer.assign(partitions);
            for (var tp : partitions) {
                consumer.seek(tp, 0L);
            }

            var collectedRecords = new ArrayList<String>();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(500)).forEach(r -> collectedRecords.add(r.value()));
                        assertThat(collectedRecords)
                                .withFailMessage("No records received from order-shipment-failed-topic")
                                .isNotEmpty();

                        var compensationEvent = collectedRecords.stream()
                                .map(v -> {
                                    try {
                                        return objectMapper.readValue(v, OrderShipmentFailedEvent.class);
                                    } catch (Exception e) {
                                        throw new AssertionError("Failed to deserialize OrderShipmentFailedEvent: " + v, e);
                                    }
                                })
                                .filter(e -> orderResponse.id().equals(e.orderId()))
                                .findFirst();

                        assertThat(compensationEvent).isPresent();
                        assertThat(compensationEvent.get().orderNumber()).isEqualTo(orderResponse.orderNumber());
                        assertThat(compensationEvent.get().reason()).isEqualTo("Carrier rejected");
                    });
        }
    }

    private OrderResponse createPendingOrder(String skuCode, int quantity) {
        var response = testRestTemplate.postForEntity(
                "http://localhost:" + port + "/api/orders",
                new CreateOrderRequest(skuCode, new BigDecimal("29.90"), quantity),
                OrderResponse.class
        );
        assertThat(response.getStatusCode())
                .withFailMessage("Expected CREATED but got %s", response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        var order = response.getBody();
        assertThat(order).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        return order;
    }

    private OrderResponse getOrder(Long orderId) {
        var response = testRestTemplate.getForEntity(
                "http://localhost:" + port + "/api/orders/" + orderId,
                OrderResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
