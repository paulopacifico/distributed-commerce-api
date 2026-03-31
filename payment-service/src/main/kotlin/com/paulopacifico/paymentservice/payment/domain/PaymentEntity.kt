package com.paulopacifico.paymentservice.payment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime

@Entity
@Table(name = "payments")
class PaymentEntity(
    @Column(name = "order_id", nullable = false, unique = true)
    val orderId: Long,

    @Column(name = "order_number", nullable = false, length = 64)
    val orderNumber: String,

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, length = 16)
    var status: String,

    @Column(name = "failure_reason", length = 255)
    var failureReason: String? = null,

    @Column(name = "processed_at", nullable = false)
    val processedAt: OffsetDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
