package com.paulopacifico.orderservice.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paulopacifico.orderservice.order.api.CreateOrderRequest;
import com.paulopacifico.orderservice.order.api.OrderMapper;
import com.paulopacifico.orderservice.order.api.OrderResponse;
import com.paulopacifico.orderservice.order.domain.OrderEntity;
import com.paulopacifico.orderservice.order.domain.OrderStatus;
import com.paulopacifico.orderservice.order.messaging.OrderPlacedEventPublisher;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderPlacedEventPublisher orderPlacedEventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, new OrderMapper(), orderPlacedEventPublisher);
    }

    @Test
    void shouldCreatePendingOrder() {
        CreateOrderRequest request = new CreateOrderRequest("SKU-100", new BigDecimal("19.99"), 2);
        OrderEntity persisted = new OrderEntity(
                "ORD-001",
                "SKU-100",
                new BigDecimal("19.99"),
                2,
                OrderStatus.PENDING,
                OffsetDateTime.now()
        );

        when(orderRepository.save(any(OrderEntity.class))).thenReturn(persisted);

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.orderNumber()).isEqualTo("ORD-001");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(any(OrderEntity.class));
        verify(orderPlacedEventPublisher).publish(any());
    }

    @Test
    void shouldThrowWhenOrderIsMissing() {
        when(orderRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order with id 99 was not found");
    }
}
