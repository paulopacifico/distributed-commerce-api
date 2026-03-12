package com.paulopacifico.inventoryservice.messaging.api

import java.time.OffsetDateTime
import java.util.UUID

data class InventoryReservedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val skuCode: String,
    val reservedQuantity: Int,
    val occurredAt: OffsetDateTime,
)
