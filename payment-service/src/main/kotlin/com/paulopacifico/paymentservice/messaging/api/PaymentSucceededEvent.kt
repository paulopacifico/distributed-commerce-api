package com.paulopacifico.paymentservice.messaging.api

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class PaymentSucceededEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val amount: BigDecimal,
    val occurredAt: OffsetDateTime,
)
