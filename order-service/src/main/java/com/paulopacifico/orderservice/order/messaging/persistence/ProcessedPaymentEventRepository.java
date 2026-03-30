package com.paulopacifico.orderservice.order.messaging.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEventEntity, UUID> {
}
