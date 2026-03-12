package com.paulopacifico.orderservice.order.api;

import com.paulopacifico.orderservice.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OrderResponse(
        Long id,
        String orderNumber,
        String skuCode,
        BigDecimal price,
        Integer quantity,
        OrderStatus status,
        OffsetDateTime createdAt
) {
}
