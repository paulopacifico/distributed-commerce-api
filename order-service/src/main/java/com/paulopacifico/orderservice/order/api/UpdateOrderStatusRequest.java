package com.paulopacifico.orderservice.order.api;

import com.paulopacifico.orderservice.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "status is required")
        OrderStatus status
) {
}
