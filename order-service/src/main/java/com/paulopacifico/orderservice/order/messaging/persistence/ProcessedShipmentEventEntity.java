package com.paulopacifico.orderservice.order.messaging.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_shipment_events")
public class ProcessedShipmentEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private OffsetDateTime processedAt;

    protected ProcessedShipmentEventEntity() {
    }

    public ProcessedShipmentEventEntity(UUID eventId, Long orderId, OffsetDateTime processedAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.processedAt = processedAt;
    }

    public UUID getEventId() { return eventId; }
    public Long getOrderId() { return orderId; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
}
