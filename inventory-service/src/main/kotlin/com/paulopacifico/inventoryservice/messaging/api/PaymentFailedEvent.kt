package com.paulopacifico.inventoryservice.messaging.api

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class PaymentFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val amount: BigDecimal,
    val reason: String,
    val occurredAt: OffsetDateTime,
)
