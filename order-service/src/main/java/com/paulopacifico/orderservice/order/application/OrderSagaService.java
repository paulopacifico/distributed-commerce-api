package com.paulopacifico.orderservice.order.application;

import com.paulopacifico.orderservice.messaging.api.OrderFailedEvent;
import com.paulopacifico.orderservice.order.api.OrderMapper;
import com.paulopacifico.orderservice.order.api.OrderResponse;
import com.paulopacifico.orderservice.order.domain.OrderEntity;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import com.paulopacifico.orderservice.order.messaging.OrderFailedEventPublisher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderFailedEventPublisher orderFailedEventPublisher;

    public OrderSagaService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            OrderFailedEventPublisher orderFailedEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.orderFailedEventPublisher = orderFailedEventPublisher;
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus status) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        applyStatusTransition(order, status);
        return orderMapper.toResponse(order);
    }

    @Transactional
    public void confirmOrder(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        applyStatusTransition(order, OrderStatus.CONFIRMED);
    }

    @Transactional
    public void failOrder(Long orderId, String reason) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        OrderStatus statusBefore = order.getStatus();
        applyStatusTransition(order, OrderStatus.FAILED);
        if (statusBefore == OrderStatus.PENDING && order.getStatus() == OrderStatus.FAILED) {
            var event = new OrderFailedEvent(
                    UUID.randomUUID(),
                    order.getId(),
                    order.getOrderNumber(),
                    order.getSkuCode(),
                    order.getQuantity(),
                    reason,
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            orderFailedEventPublisher.publish(event);
            log.info("Published OrderFailedEvent eventId={} orderId={} orderNumber={}", event.eventId(), order.getId(), order.getOrderNumber());
        }
        log.info("Marked order id={} orderNumber={} as FAILED reason={}", order.getId(), order.getOrderNumber(), reason);
    }

    private void applyStatusTransition(OrderEntity order, OrderStatus status) {
        if (order.getStatus() == status) {
            log.info(
                    "Ignoring duplicate order status transition id={} orderNumber={} status={}",
                    order.getId(), order.getOrderNumber(), status
            );
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn(
                    "Ignoring out-of-order status transition id={} orderNumber={} currentStatus={} requestedStatus={}",
                    order.getId(), order.getOrderNumber(), order.getStatus(), status
            );
            return;
        }

        if (status == OrderStatus.CONFIRMED) {
            order.confirm();
        } else if (status == OrderStatus.FAILED) {
            order.fail();
        }

        log.info("Updated order id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), order.getStatus());
    }
}
