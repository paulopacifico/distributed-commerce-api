package com.paulopacifico.paymentservice.payment.messaging.persistence

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedOrderConfirmedEventRepository : JpaRepository<ProcessedOrderConfirmedEventEntity, UUID>
