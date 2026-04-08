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
        String paymentFailed,
        String orderPaid,
        String shipmentShipped,
        String shipmentFailed
) {
}
