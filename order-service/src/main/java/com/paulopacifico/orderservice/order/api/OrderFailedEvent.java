package com.paulopacifico.orderservice.order.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderFailedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        String reason,
        OffsetDateTime occurredAt
) {
}
