package com.paulopacifico.inventoryservice.inventory.messaging

import com.paulopacifico.inventoryservice.inventory.application.InventoryService
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.InventoryReservationRepository
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventEntity
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventRepository
import com.paulopacifico.inventoryservice.messaging.api.OrderFailedEvent
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFailedSagaConsumer(
    private val inventoryService: InventoryService,
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val processedOrderEventRepository: ProcessedOrderEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @KafkaListener(
        id = "orderFailedSagaConsumer",
        topics = ["\${app.kafka.topics.order-failed}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumeOrderFailed(event: OrderFailedEvent) {
        if (processedOrderEventRepository.existsById(event.eventId)) {
            logger.info("Ignoring duplicate OrderFailedEvent eventId={} orderId={}", event.eventId, event.orderId)
            return
        }

        val reservation = inventoryReservationRepository.findById(event.orderId).orElse(null)
        if (reservation == null) {
            logger.warn(
                "No reservation found for orderId={}, skipping compensation (order may have failed before reservation)",
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
        logger.info("Processed OrderFailedEvent eventId={} orderId={}", event.eventId, event.orderId)
    }
}
