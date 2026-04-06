package com.paulopacifico.shipmentservice.messaging.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.kafka.topics")
data class KafkaTopicProperties(
    var orderPaid: String = "order-paid-topic",
    var shipmentShipped: String = "shipment-shipped-topic",
    var shipmentFailed: String = "shipment-failed-topic",
)
