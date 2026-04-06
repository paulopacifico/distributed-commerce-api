package com.paulopacifico.shipmentservice.messaging.api

import java.time.OffsetDateTime
import java.util.UUID

data class ShipmentShippedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val occurredAt: OffsetDateTime,
)
