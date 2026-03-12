package com.paulopacifico.inventoryservice.inventory.application

import com.paulopacifico.inventoryservice.inventory.domain.InventoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InventoryRepository : JpaRepository<InventoryEntity, Long> {
    fun findBySkuCode(skuCode: String): InventoryEntity?
}
