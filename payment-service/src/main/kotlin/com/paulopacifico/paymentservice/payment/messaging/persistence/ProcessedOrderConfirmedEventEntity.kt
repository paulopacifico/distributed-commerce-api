package com.paulopacifico.paymentservice.payment.messaging.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "processed_order_confirmed_events")
class ProcessedOrderConfirmedEventEntity(
    @Id
    val eventId: UUID,

    @Column(nullable = false)
    val orderId: Long,

    @Column(nullable = false)
    val processedAt: OffsetDateTime,
)
