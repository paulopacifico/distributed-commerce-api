package com.paulopacifico.paymentservice.messaging.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.kafka.topics")
data class KafkaTopicProperties(
    var orderConfirmed: String = "order-confirmed-topic",
    var paymentSucceeded: String = "payment-succeeded-topic",
    var paymentFailed: String = "payment-failed-topic",
)
