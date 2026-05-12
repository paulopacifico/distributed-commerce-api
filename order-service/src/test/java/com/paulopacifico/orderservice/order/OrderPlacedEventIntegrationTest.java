package com.paulopacifico.orderservice.order;

import com.paulopacifico.orderservice.messaging.api.OrderPlacedEvent;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.TopicPartition;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderPlacedEventIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    void shouldCreateOrderAndPublishOrderPlacedEvent() {
        var headers = authHeaders(port, testRestTemplate);
        ResponseEntity<String> response = testRestTemplate.exchange(
                "http://localhost:" + port + "/api/orders",
                HttpMethod.POST,
                new HttpEntity<>(new CreateOrderRequest("SKU-IT-100", new BigDecimal("49.90"), 3), headers),
                String.class
        );

        assertThat(response.getStatusCode())
                .withFailMessage("Expected CREATED but got %s with body: %s", response.getStatusCode(), response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        OrderResponse orderResponse;
        try {
            orderResponse = objectMapper.readValue(response.getBody(), OrderResponse.class);
        } catch (Exception exception) {
            throw new AssertionError("Failed to deserialize successful order response body: " + response.getBody(), exception);
        }

        assertThat(orderResponse.status()).isEqualTo(OrderStatus.PENDING);

        try (var consumer = kafkaConsumer("order-service-it")) {
            // Use manual partition assignment + seek(tp, 0) rather than subscribe +
            // seekToBeginning.  seekToBeginning is lazy and requires a ListOffsets
            // round-trip on the next poll; under CI load that trip can exceed the
            // per-poll window and the 15-second budget runs out before records arrive.
            // seek(tp, 0) sets the fetch position directly in the subscription state
            // with no network round-trip — the same approach used in OrderSagaIntegrationTest.
            var partitions = List.of(
                    new TopicPartition("order-placed-topic", 0),
                    new TopicPartition("order-placed-topic", 1),
                    new TopicPartition("order-placed-topic", 2)
            );
            consumer.assign(partitions);
            for (var tp : partitions) {
                consumer.seek(tp, 0L);
            }

            // Accumulate across polls so that records fetched by an earlier poll are
            // not lost when Awaitility retries — mirrors the pattern in OrderSagaIntegrationTest.
            var collectedValues = new ArrayList<String>();

            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(500))
                                .forEach(r -> collectedValues.add(r.value()));

                        assertThat(collectedValues)
                                .withFailMessage("No records received from order-placed-topic")
                                .isNotEmpty();

                        var matchingEvent = collectedValues.stream()
                                .map(v -> {
                                    try {
                                        return objectMapper.readValue(v, OrderPlacedEvent.class);
                                    } catch (Exception e) {
                                        throw new AssertionError("Failed to deserialize OrderPlacedEvent: " + v, e);
                                    }
                                })
                                .filter(e -> orderResponse.orderNumber().equals(e.orderNumber()))
                                .findFirst();

                        assertThat(matchingEvent)
                                .withFailMessage("OrderPlacedEvent for orderNumber=%s not found in %d record(s)",
                                        orderResponse.orderNumber(), collectedValues.size())
                                .isPresent();
                        assertThat(matchingEvent.get().skuCode()).isEqualTo("SKU-IT-100");
                        assertThat(matchingEvent.get().quantity()).isEqualTo(3);
                    });
        }
    }
}
