package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.order.api.OrderConfirmedEvent;

public interface OrderConfirmedEventPublisher {
    void publish(OrderConfirmedEvent event);
}
