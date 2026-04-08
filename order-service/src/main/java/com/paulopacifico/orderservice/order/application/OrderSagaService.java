package com.paulopacifico.orderservice.order.application;

import com.paulopacifico.orderservice.messaging.api.OrderFailedEvent;
import com.paulopacifico.orderservice.messaging.api.OrderPaidEvent;
import com.paulopacifico.orderservice.order.api.OrderConfirmedEvent;
import com.paulopacifico.orderservice.order.api.OrderMapper;
import com.paulopacifico.orderservice.order.api.OrderResponse;
import com.paulopacifico.orderservice.order.domain.OrderEntity;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import com.paulopacifico.orderservice.order.messaging.OrderConfirmedEventPublisher;
import com.paulopacifico.orderservice.order.messaging.OrderFailedEventPublisher;
import com.paulopacifico.orderservice.order.messaging.OrderPaidEventPublisher;
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
    private final OrderConfirmedEventPublisher orderConfirmedEventPublisher;
    private final OrderPaidEventPublisher orderPaidEventPublisher;

    public OrderSagaService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            OrderFailedEventPublisher orderFailedEventPublisher,
            OrderConfirmedEventPublisher orderConfirmedEventPublisher,
            OrderPaidEventPublisher orderPaidEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.orderFailedEventPublisher = orderFailedEventPublisher;
        this.orderConfirmedEventPublisher = orderConfirmedEventPublisher;
        this.orderPaidEventPublisher = orderPaidEventPublisher;
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
        boolean transitioned = applyStatusTransition(order, OrderStatus.CONFIRMED);
        if (transitioned) {
            var event = new OrderConfirmedEvent(
                    UUID.randomUUID(),
                    order.getId(),
                    order.getOrderNumber(),
                    order.getPrice(),
                    order.getQuantity(),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            orderConfirmedEventPublisher.publish(event);
        }
        log.info("Marked order id={} orderNumber={} as CONFIRMED", order.getId(), order.getOrderNumber());
    }

    @Transactional
    public void failOrder(Long orderId, String reason) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyStatusTransition(order, OrderStatus.FAILED);
        if (transitioned) {
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

    @Transactional
    public void markAsPaid(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyPaymentTransition(order, OrderStatus.PAID);
        if (transitioned) {
            var event = new OrderPaidEvent(
                    UUID.randomUUID(),
                    order.getId(),
                    order.getOrderNumber(),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
            orderPaidEventPublisher.publish(event);
            log.info("Published OrderPaidEvent eventId={} orderId={} orderNumber={}", event.eventId(), order.getId(), order.getOrderNumber());
        }
        log.info("Marked order id={} orderNumber={} as PAID", order.getId(), order.getOrderNumber());
    }

    @Transactional
    public void markAsPaymentFailed(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyPaymentTransition(order, OrderStatus.PAYMENT_FAILED);
        if (transitioned) {
            log.info("Marked order id={} orderNumber={} as PAYMENT_FAILED", order.getId(), order.getOrderNumber());
        }
    }

    @Transactional
    public void markAsShipped(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyShipmentTransition(order, OrderStatus.SHIPPED);
        if (transitioned) {
            log.info("Marked order id={} orderNumber={} as SHIPPED", order.getId(), order.getOrderNumber());
        }
    }

    @Transactional
    public void markAsShipmentFailed(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        boolean transitioned = applyShipmentTransition(order, OrderStatus.SHIPMENT_FAILED);
        if (transitioned) {
            log.info("Marked order id={} orderNumber={} as SHIPMENT_FAILED", order.getId(), order.getOrderNumber());
        }
    }

    private boolean applyPaymentTransition(OrderEntity order, OrderStatus target) {
        if (order.getStatus() == target) {
            log.info("Ignoring duplicate payment status transition id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), target);
            return false;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.warn("Ignoring payment transition for order not in CONFIRMED state id={} orderNumber={} currentStatus={} requestedStatus={}", order.getId(), order.getOrderNumber(), order.getStatus(), target);
            return false;
        }
        if (target == OrderStatus.PAID) {
            order.pay();
        } else if (target == OrderStatus.PAYMENT_FAILED) {
            order.failPayment();
        }
        log.info("Updated order id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), order.getStatus());
        return true;
    }

    private boolean applyShipmentTransition(OrderEntity order, OrderStatus target) {
        if (order.getStatus() == target) {
            log.info("Ignoring duplicate shipment status transition id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), target);
            return false;
        }
        if (order.getStatus() != OrderStatus.PAID) {
            log.warn("Ignoring shipment transition for order not in PAID state id={} orderNumber={} currentStatus={} requestedStatus={}", order.getId(), order.getOrderNumber(), order.getStatus(), target);
            return false;
        }
        if (target == OrderStatus.SHIPPED) {
            order.ship();
        } else if (target == OrderStatus.SHIPMENT_FAILED) {
            order.failShipment();
        }
        log.info("Updated order id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), order.getStatus());
        return true;
    }

    private boolean applyStatusTransition(OrderEntity order, OrderStatus status) {
        if (order.getStatus() == status) {
            log.info("Ignoring duplicate order status transition id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), status);
            return false;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Ignoring out-of-order status transition id={} orderNumber={} currentStatus={} requestedStatus={}", order.getId(), order.getOrderNumber(), order.getStatus(), status);
            return false;
        }
        if (status == OrderStatus.CONFIRMED) {
            order.confirm();
        } else if (status == OrderStatus.FAILED) {
            order.fail();
        }
        log.info("Updated order id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), order.getStatus());
        return true;
    }
}
