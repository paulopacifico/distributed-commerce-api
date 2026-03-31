package com.paulopacifico.paymentservice.messaging.api

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class OrderConfirmedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val price: BigDecimal,
    val quantity: Int,
    val occurredAt: OffsetDateTime,
)
