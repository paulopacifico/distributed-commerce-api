package com.paulopacifico.orderservice.messaging.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderShipmentFailedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        String reason,
        OffsetDateTime occurredAt
) {
}
