package com.paulopacifico.inventoryservice.inventory.api

data class InventoryResponse(
    val id: Long,
    val skuCode: String,
    val quantity: Int,
)
