package com.paulopacifico.inventoryservice.inventory.application

class InventoryNotFoundException(
    skuCode: String,
) : RuntimeException("Inventory for skuCode '$skuCode' was not found")
