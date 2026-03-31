package com.paulopacifico.paymentservice.payment.application

import com.paulopacifico.paymentservice.payment.domain.PaymentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<PaymentEntity, Long>
