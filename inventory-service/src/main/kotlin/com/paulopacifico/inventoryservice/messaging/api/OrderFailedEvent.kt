package com.paulopacifico.inventoryservice.messaging.api

import java.time.OffsetDateTime
import java.util.UUID

data class OrderFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val skuCode: String,
    val reservedQuantity: Int,
    val reason: String,
    val occurredAt: OffsetDateTime,
)
