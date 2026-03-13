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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

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
        ResponseEntity<OrderResponse> response = testRestTemplate.postForEntity(
                "http://localhost:" + port + "/api/orders",
                new CreateOrderRequest("SKU-IT-100", new BigDecimal("49.90"), 3),
                OrderResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING);

        try (var consumer = kafkaConsumer("order-service-it")) {
            consumer.subscribe(List.of("order-placed-topic"));

            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .until(() -> {
                        consumer.poll(Duration.ofMillis(200));
                        return !consumer.assignment().isEmpty();
                    });

            Awaitility.await()
                    .atMost(Duration.ofSeconds(15))
                    .untilAsserted(() -> {
                        var records = consumer.poll(Duration.ofMillis(500));
                        assertThat(records).isNotEmpty();

                        var record = records.iterator().next();
                        var event = objectMapper.readValue(record.value(), OrderPlacedEvent.class);

                        assertThat(event.orderNumber()).isEqualTo(response.getBody().orderNumber());
                        assertThat(event.skuCode()).isEqualTo("SKU-IT-100");
                        assertThat(event.quantity()).isEqualTo(3);
                    });
        }
    }
}
