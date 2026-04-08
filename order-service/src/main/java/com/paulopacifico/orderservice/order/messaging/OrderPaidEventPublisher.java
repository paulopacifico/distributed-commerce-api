package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.messaging.api.OrderPaidEvent;

public interface OrderPaidEventPublisher {
    void publish(OrderPaidEvent event);
}
