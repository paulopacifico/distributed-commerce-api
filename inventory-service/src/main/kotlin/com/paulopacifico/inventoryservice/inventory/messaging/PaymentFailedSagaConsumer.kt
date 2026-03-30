package com.paulopacifico.inventoryservice.inventory.messaging

import com.paulopacifico.inventoryservice.inventory.application.InventoryService
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.InventoryReservationRepository
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventEntity
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventRepository
import com.paulopacifico.inventoryservice.messaging.api.PaymentFailedEvent
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentFailedSagaConsumer(
    private val inventoryService: InventoryService,
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val processedOrderEventRepository: ProcessedOrderEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @KafkaListener(
        id = "paymentFailedSagaConsumer",
        topics = ["\${app.kafka.topics.payment-failed}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumePaymentFailed(event: PaymentFailedEvent) {
        if (processedOrderEventRepository.existsById(event.eventId)) {
            logger.info("Ignoring duplicate PaymentFailedEvent eventId={} orderId={}", event.eventId, event.orderId)
            return
        }

        val reservation = inventoryReservationRepository.findById(event.orderId).orElse(null)
        if (reservation == null) {
            logger.warn(
                "No reservation found for orderId={}, skipping inventory release on payment failure",
                event.orderId,
            )
        } else {
            inventoryService.releaseInventory(reservation.skuCode, reservation.reservedQuantity)
            inventoryReservationRepository.delete(reservation)
        }

        processedOrderEventRepository.save(
            ProcessedOrderEventEntity(
                eventId = event.eventId,
                orderId = event.orderId,
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
        logger.info("Processed PaymentFailedEvent eventId={} orderId={} reason={}", event.eventId, event.orderId, event.reason)
    }
}
