package com.paulopacifico.orderservice.order.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        String skuCode,
        BigDecimal price,
        Integer quantity,
        OffsetDateTime occurredAt
) {
}
