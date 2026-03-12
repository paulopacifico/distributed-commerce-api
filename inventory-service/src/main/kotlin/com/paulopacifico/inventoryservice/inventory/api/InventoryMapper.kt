package com.paulopacifico.inventoryservice.inventory.api

import com.paulopacifico.inventoryservice.inventory.domain.InventoryEntity
import org.springframework.stereotype.Component

@Component
class InventoryMapper {
    fun toEntity(request: CreateInventoryRequest): InventoryEntity =
        InventoryEntity(
            skuCode = request.skuCode.trim(),
            quantity = request.quantity,
        )

    fun toResponse(entity: InventoryEntity): InventoryResponse =
        InventoryResponse(
            id = requireNotNull(entity.id) { "Inventory id must not be null when mapping response" },
            skuCode = entity.skuCode,
            quantity = entity.quantity,
        )

    fun toReservationResponse(entity: InventoryEntity, requestedQuantity: Int): InventoryReservationResponse =
        InventoryReservationResponse(
            skuCode = entity.skuCode,
            requestedQuantity = requestedQuantity,
            remainingQuantity = entity.quantity,
            reserved = true,
        )
}
