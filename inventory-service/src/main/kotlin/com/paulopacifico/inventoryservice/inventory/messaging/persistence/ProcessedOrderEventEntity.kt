package com.paulopacifico.inventoryservice.inventory.messaging.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "processed_order_events")
class ProcessedOrderEventEntity(
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    var eventId: UUID,
    @Column(name = "order_id", nullable = false)
    var orderId: Long,
    @Column(name = "processed_at", nullable = false, updatable = false)
    var processedAt: OffsetDateTime,
) {
    protected constructor() : this(
        eventId = UUID.randomUUID(),
        orderId = 0L,
        processedAt = OffsetDateTime.MIN,
    )
}
