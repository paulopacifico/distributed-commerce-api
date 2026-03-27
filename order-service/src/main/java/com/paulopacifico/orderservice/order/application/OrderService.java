package com.paulopacifico.orderservice.order.application;

import com.paulopacifico.orderservice.order.api.CreateOrderRequest;
import com.paulopacifico.orderservice.order.api.OrderMapper;
import com.paulopacifico.orderservice.order.api.OrderResponse;
import com.paulopacifico.orderservice.order.domain.OrderEntity;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import com.paulopacifico.orderservice.order.messaging.OrderPlacedEventPublisher;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderPlacedEventPublisher orderPlacedEventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            OrderPlacedEventPublisher orderPlacedEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.orderPlacedEventPublisher = orderPlacedEventPublisher;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        String orderNumber = UUID.randomUUID().toString();
        OrderEntity order = orderMapper.toEntity(request, orderNumber);
        OrderEntity savedOrder = orderRepository.save(order);

        if (savedOrder.getStatus() == OrderStatus.PENDING) {
            var orderPlacedEvent = orderMapper.toPlacedEvent(savedOrder);
            orderPlacedEventPublisher.publish(orderPlacedEvent);
            log.info(
                    "Created order id={} orderNumber={} skuCode={} eventId={}",
                    savedOrder.getId(),
                    savedOrder.getOrderNumber(),
                    savedOrder.getSkuCode(),
                    orderPlacedEvent.eventId()
            );
        }

        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(orderMapper::toResponse);
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
        applyStatusTransition(order, OrderStatus.FAILED);
        log.info(
                "Marked order id={} orderNumber={} as FAILED reason={}",
                order.getId(),
                order.getOrderNumber(),
                reason
        );
    }

    private void applyStatusTransition(OrderEntity order, OrderStatus status) {
        if (order.getStatus() == status) {
            log.info(
                    "Ignoring duplicate order status transition id={} orderNumber={} status={}",
                    order.getId(),
                    order.getOrderNumber(),
                    status
            );
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn(
                    "Ignoring out-of-order status transition id={} orderNumber={} currentStatus={} requestedStatus={}",
                    order.getId(),
                    order.getOrderNumber(),
                    order.getStatus(),
                    status
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
