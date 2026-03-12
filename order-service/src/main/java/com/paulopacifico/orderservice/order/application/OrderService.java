package com.paulopacifico.orderservice.order.application;

import com.paulopacifico.orderservice.order.api.CreateOrderRequest;
import com.paulopacifico.orderservice.order.api.OrderCreatedEvent;
import com.paulopacifico.orderservice.order.api.OrderMapper;
import com.paulopacifico.orderservice.order.api.OrderResponse;
import com.paulopacifico.orderservice.order.domain.OrderEntity;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderService(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        String orderNumber = UUID.randomUUID().toString();
        OrderEntity order = orderMapper.toEntity(request, orderNumber);
        OrderEntity savedOrder = orderRepository.save(order);

        OrderCreatedEvent orderCreatedEvent = orderMapper.toCreatedEvent(savedOrder);
        log.info(
                "Created order id={} orderNumber={} skuCode={} eventId={}",
                savedOrder.getId(),
                savedOrder.getOrderNumber(),
                savedOrder.getSkuCode(),
                orderCreatedEvent.eventId()
        );

        return orderMapper.toResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .map(orderMapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus status) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (status == OrderStatus.CONFIRMED) {
            order.confirm();
        } else if (status == OrderStatus.FAILED) {
            order.fail();
        }

        log.info("Updated order id={} orderNumber={} status={}", order.getId(), order.getOrderNumber(), order.getStatus());
        return orderMapper.toResponse(order);
    }
}
