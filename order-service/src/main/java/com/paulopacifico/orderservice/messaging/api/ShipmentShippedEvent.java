package com.paulopacifico.orderservice.messaging.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ShipmentShippedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        OffsetDateTime occurredAt
) {
}
