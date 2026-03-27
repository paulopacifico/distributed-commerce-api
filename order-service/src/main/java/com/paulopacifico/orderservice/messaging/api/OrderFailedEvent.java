package com.paulopacifico.orderservice.messaging.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderFailedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        String skuCode,
        Integer reservedQuantity,
        String reason,
        OffsetDateTime occurredAt
) {
}
