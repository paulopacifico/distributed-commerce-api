package com.paulopacifico.inventoryservice.messaging.api

import java.time.OffsetDateTime
import java.util.UUID

data class OrderShipmentFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val reason: String,
    val occurredAt: OffsetDateTime,
)
