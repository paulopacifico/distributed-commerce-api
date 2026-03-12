package com.paulopacifico.inventoryservice.inventory.api

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class ReserveInventoryRequest(
    @field:NotBlank(message = "skuCode is required")
    val skuCode: String,
    @field:Min(value = 1, message = "quantity must be at least 1")
    val quantity: Int,
)
