package com.paulopacifico.shipmentservice.messaging.api

import java.time.OffsetDateTime
import java.util.UUID

data class OrderPaidEvent(
    val eventId: UUID,
    val orderId: Long,
    val orderNumber: String,
    val occurredAt: OffsetDateTime,
)
