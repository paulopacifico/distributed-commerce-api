package com.paulopacifico.paymentservice.payment.messaging

import com.paulopacifico.paymentservice.messaging.api.OrderConfirmedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentFailedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentSucceededEvent
import com.paulopacifico.paymentservice.payment.application.PaymentService
import com.paulopacifico.paymentservice.payment.messaging.persistence.ProcessedOrderConfirmedEventEntity
import com.paulopacifico.paymentservice.payment.messaging.persistence.ProcessedOrderConfirmedEventRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderConfirmedSagaConsumer(
    private val paymentService: PaymentService,
    private val paymentSagaEventPublisher: PaymentSagaEventPublisher,
    private val processedOrderConfirmedEventRepository: ProcessedOrderConfirmedEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @KafkaListener(
        id = "orderConfirmedSagaConsumer",
        topics = ["\${app.kafka.topics.order-confirmed}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumeOrderConfirmed(event: OrderConfirmedEvent) {
        if (processedOrderConfirmedEventRepository.existsById(event.eventId)) {
            logger.info("Ignoring duplicate OrderConfirmedEvent eventId={} orderId={}", event.eventId, event.orderId)
            return
        }

        val payment = paymentService.processPayment(event)

        if (payment.status == "SUCCEEDED") {
            paymentSagaEventPublisher.publishSucceeded(
                PaymentSucceededEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    amount = payment.amount,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        } else {
            paymentSagaEventPublisher.publishFailed(
                PaymentFailedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    amount = payment.amount,
                    reason = payment.failureReason ?: "Unknown payment failure",
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        }

        processedOrderConfirmedEventRepository.save(
            ProcessedOrderConfirmedEventEntity(
                eventId = event.eventId,
                orderId = event.orderId,
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
        logger.info("Processed OrderConfirmedEvent eventId={} orderId={} paymentStatus={}", event.eventId, event.orderId, payment.status)
    }
}
