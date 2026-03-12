package com.paulopacifico.orderservice.order.application;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Order with id %d was not found".formatted(orderId));
    }
}
