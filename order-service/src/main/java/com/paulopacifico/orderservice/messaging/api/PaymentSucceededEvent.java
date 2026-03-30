package com.paulopacifico.orderservice.messaging.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        BigDecimal amount,
        OffsetDateTime occurredAt
) {
}
