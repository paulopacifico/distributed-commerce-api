package com.paulopacifico.inventoryservice.inventory.messaging.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface InventoryReservationRepository : JpaRepository<InventoryReservationEntity, Long>
