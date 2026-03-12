package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.messaging.api.OrderPlacedEvent;

public interface OrderPlacedEventPublisher {

    void publish(OrderPlacedEvent event);
}
