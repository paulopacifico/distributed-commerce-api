package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.messaging.api.OrderShipmentFailedEvent;

public interface OrderShipmentFailedEventPublisher {
    void publish(OrderShipmentFailedEvent event);
}
