package com.paulopacifico.inventoryservice.inventory.application

class InsufficientInventoryException(
    skuCode: String,
    requestedQuantity: Int,
    availableQuantity: Int,
) : RuntimeException(
        "Insufficient inventory for skuCode '$skuCode'. Requested=$requestedQuantity, available=$availableQuantity",
    )
