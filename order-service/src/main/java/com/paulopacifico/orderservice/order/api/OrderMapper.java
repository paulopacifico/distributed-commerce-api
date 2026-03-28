package com.paulopacifico.orderservice.order.api;

import com.paulopacifico.orderservice.messaging.api.OrderPlacedEvent;
import com.paulopacifico.orderservice.order.domain.OrderEntity;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderEntity toEntity(CreateOrderRequest request, String orderNumber) {
        return new OrderEntity(
                orderNumber,
                request.skuCode(),
                request.price(),
                request.quantity(),
                OrderStatus.PENDING,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public OrderResponse toResponse(OrderEntity entity) {
        return new OrderResponse(
                entity.getId(),
                entity.getOrderNumber(),
                entity.getSkuCode(),
                entity.getPrice(),
                entity.getQuantity(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }

    public OrderPlacedEvent toPlacedEvent(OrderEntity entity) {
        return new OrderPlacedEvent(
                UUID.randomUUID(),
                entity.getId(),
                entity.getOrderNumber(),
                entity.getSkuCode(),
                entity.getPrice(),
                entity.getQuantity(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public OrderConfirmedEvent toConfirmedEvent(OrderEntity entity) {
        return new OrderConfirmedEvent(
                UUID.randomUUID(),
                entity.getId(),
                entity.getOrderNumber(),
                entity.getPrice(),
                entity.getQuantity(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public OrderFailedEvent toFailedEvent(OrderEntity entity, String reason) {
        return new OrderFailedEvent(
                UUID.randomUUID(),
                entity.getId(),
                entity.getOrderNumber(),
                reason,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }
}
