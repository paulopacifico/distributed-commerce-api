package com.paulopacifico.inventoryservice.inventory.api

data class InventoryReservationResponse(
    val skuCode: String,
    val requestedQuantity: Int,
    val remainingQuantity: Int,
    val reserved: Boolean,
)
