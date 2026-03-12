package com.paulopacifico.orderservice.messaging.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryReservedEvent(
        UUID eventId,
        Long orderId,
        String orderNumber,
        String skuCode,
        Integer reservedQuantity,
        OffsetDateTime occurredAt
) {
}
