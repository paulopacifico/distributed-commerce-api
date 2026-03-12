package com.paulopacifico.inventoryservice.messaging.api

import java.time.OffsetDateTime
import java.util.UUID

data class InventoryFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val skuCode: String,
    val requestedQuantity: Int,
    val reason: String,
    val occurredAt: OffsetDateTime,
)
