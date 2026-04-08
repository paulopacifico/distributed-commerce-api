package com.paulopacifico.orderservice.order.domain;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    PAID,
    PAYMENT_FAILED,
    SHIPPED,
    SHIPMENT_FAILED
}
