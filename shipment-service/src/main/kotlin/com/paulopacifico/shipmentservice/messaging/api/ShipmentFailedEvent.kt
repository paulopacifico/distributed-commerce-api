package com.paulopacifico.shipmentservice.messaging.api

import java.time.OffsetDateTime
import java.util.UUID

data class ShipmentFailedEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val reason: String,
    val occurredAt: OffsetDateTime,
)
