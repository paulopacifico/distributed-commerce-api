package com.paulopacifico.inventoryservice.inventory.api

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateInventoryRequest(
    @field:NotBlank(message = "skuCode is required")
    val skuCode: String,
    @field:Min(value = 0, message = "quantity must be zero or greater")
    val quantity: Int,
)
