package com.paulopacifico.shipmentservice.shipment.messaging.persistence

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedOrderPaidEventRepository : JpaRepository<ProcessedOrderPaidEventEntity, UUID>
