package com.paulopacifico.inventoryservice.inventory.messaging.persistence

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedOrderEventRepository : JpaRepository<ProcessedOrderEventEntity, UUID>
