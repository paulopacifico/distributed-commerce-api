package com.paulopacifico.orderservice.order;

import com.paulopacifico.orderservice.messaging.api.InventoryFailedEvent;
import com.paulopacifico.orderservice.messaging.api.InventoryReservedEvent;
import com.paulopacifico.orderservice.messaging.api.OrderFailedEvent;
import com.paulopacifico.orderservice.order.api.CreateOrderRequest;
import com.paulopacifico.orderservice.order.api.OrderResponse;
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

        try (var consumer = kafkaConsumer("order-saga-it")) {
            consumer.subscribe(List.of("order-failed-topic"));
            Awaitility.await()
                    .atMost(Duration.ofSeconds(10))
                    .until(() -> {
                        consumer.poll(Duration.ofMillis(200));
                        return !consumer.assignment().isEmpty();
                    });
            consumer.seekToEnd(consumer.assignment());
            // Force seekToEnd to materialize before we send any events. seekToEnd is lazy:
            // it doesn't fetch the broker's end offset until the next poll. Without this,
            // the first poll in the collection loop below materializes it after the
            // compensation event is already published, positioning the consumer past the
            // record we want to read.
            consumer.poll(Duration.ofMillis(0));

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

            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> {
                        var updated = getOrder(order.id());
                        assertThat(updated.status()).isEqualTo(OrderStatus.FAILED);
                    });

            var collectedRecords = new ArrayList<String>();
            Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(500)).forEach(r -> collectedRecords.add(r.value()));
                        assertThat(collectedRecords).isNotEmpty();

                        var compensationEvent = objectMapper.readValue(collectedRecords.get(0), OrderFailedEvent.class);
                        assertThat(compensationEvent.orderId()).isEqualTo(order.id());
                        assertThat(compensationEvent.orderNumber()).isEqualTo(order.orderNumber());
                        assertThat(compensationEvent.skuCode()).isEqualTo(order.skuCode());
                        assertThat(compensationEvent.reservedQuantity()).isEqualTo(order.quantity());
                        assertThat(compensationEvent.reason()).isEqualTo("Insufficient stock");
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
