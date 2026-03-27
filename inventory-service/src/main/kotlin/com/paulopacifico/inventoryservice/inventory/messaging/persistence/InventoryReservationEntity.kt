package com.paulopacifico.inventoryservice.inventory.messaging.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "inventory_reservations")
class InventoryReservationEntity(
    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    var orderId: Long,
    @Column(name = "sku_code", nullable = false, length = 64)
    var skuCode: String,
    @Column(name = "reserved_quantity", nullable = false)
    var reservedQuantity: Int,
    @Column(name = "reserved_at", nullable = false, updatable = false)
    var reservedAt: OffsetDateTime,
) {
    protected constructor() : this(
        orderId = 0L,
        skuCode = "",
        reservedQuantity = 0,
        reservedAt = OffsetDateTime.MIN,
    )
}
