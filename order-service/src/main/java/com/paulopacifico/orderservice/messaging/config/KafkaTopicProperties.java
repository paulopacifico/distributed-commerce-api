package com.paulopacifico.orderservice.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topics")
public record KafkaTopicProperties(
        String orderPlaced,
        String inventoryReserved,
        String inventoryFailed
) {
}
