package com.paulopacifico.orderservice.messaging.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderPlacedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        String skuCode,
        BigDecimal price,
        Integer quantity,
        OffsetDateTime occurredAt
) {
}
