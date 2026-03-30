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
