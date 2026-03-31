package com.paulopacifico.paymentservice.payment.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("payment")
data class PaymentProperties(
    val successRate: Double = 1.0,
)
