package com.paulopacifico.orderservice.order.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        OffsetDateTime occurredAt
) {
}
