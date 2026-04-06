package com.paulopacifico.shipmentservice.shipment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "shipments")
class ShipmentEntity(
    @Column(name = "order_id", nullable = false, unique = true)
    val orderId: Long,

    @Column(name = "order_number", nullable = false, length = 64)
    val orderNumber: String,

    @Column(nullable = false, length = 16)
    var status: String,

    @Column(name = "failure_reason", length = 255)
    var failureReason: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
