package com.paulopacifico.inventoryservice.messaging.api

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class OrderPlacedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val skuCode: String,
    val price: BigDecimal,
    val quantity: Int,
    val occurredAt: OffsetDateTime,
)
