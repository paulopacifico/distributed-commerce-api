package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.messaging.api.OrderFailedEvent;

public interface OrderFailedEventPublisher {
    void publish(OrderFailedEvent event);
}
