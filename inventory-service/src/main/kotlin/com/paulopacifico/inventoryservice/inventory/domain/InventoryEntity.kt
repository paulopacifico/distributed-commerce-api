package com.paulopacifico.inventoryservice.inventory.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "inventory")
class InventoryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "sku_code", nullable = false, unique = true, length = 64)
    var skuCode: String,
    @Column(nullable = false)
    var quantity: Int,
) {
    fun hasEnoughQuantity(requiredQuantity: Int): Boolean = quantity >= requiredQuantity

    fun deduct(requiredQuantity: Int) {
        quantity -= requiredQuantity
    }
}
