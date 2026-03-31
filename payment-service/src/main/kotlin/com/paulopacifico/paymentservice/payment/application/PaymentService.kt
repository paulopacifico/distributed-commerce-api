package com.paulopacifico.paymentservice.payment.application

import com.paulopacifico.paymentservice.messaging.api.OrderConfirmedEvent
import com.paulopacifico.paymentservice.payment.domain.PaymentEntity
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service

@Service
@EnableConfigurationProperties(PaymentProperties::class)
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val paymentProperties: PaymentProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun processPayment(event: OrderConfirmedEvent): PaymentEntity {
        val amount = event.price.multiply(java.math.BigDecimal(event.quantity))
        val succeeded = Math.random() < paymentProperties.successRate

        val payment = if (succeeded) {
            logger.info("Payment succeeded for orderId={} orderNumber={} amount={}", event.orderId, event.orderNumber, amount)
            PaymentEntity(
                orderId = event.orderId,
                orderNumber = event.orderNumber,
                amount = amount,
                status = "SUCCEEDED",
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
        } else {
            val reason = "Payment declined by payment provider"
            logger.info("Payment failed for orderId={} orderNumber={} reason={}", event.orderId, event.orderNumber, reason)
            PaymentEntity(
                orderId = event.orderId,
                orderNumber = event.orderNumber,
                amount = amount,
                status = "FAILED",
                failureReason = reason,
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
        }

        return paymentRepository.save(payment)
    }
}
