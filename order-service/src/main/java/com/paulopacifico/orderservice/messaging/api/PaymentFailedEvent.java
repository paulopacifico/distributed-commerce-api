package com.paulopacifico.orderservice.messaging.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        String reason,
        OffsetDateTime occurredAt
) {
}
