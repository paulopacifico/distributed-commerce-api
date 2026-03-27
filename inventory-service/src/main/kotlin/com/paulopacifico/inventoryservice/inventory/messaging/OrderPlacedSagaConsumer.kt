package com.paulopacifico.inventoryservice.inventory.messaging

import com.paulopacifico.inventoryservice.inventory.application.InsufficientInventoryException
import com.paulopacifico.inventoryservice.inventory.application.InventoryNotFoundException
import com.paulopacifico.inventoryservice.inventory.application.InventoryService
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.InventoryReservationEntity
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.InventoryReservationRepository
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventEntity
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventRepository
import com.paulopacifico.inventoryservice.messaging.api.InventoryFailedEvent
import com.paulopacifico.inventoryservice.messaging.api.InventoryReservedEvent
import com.paulopacifico.inventoryservice.messaging.api.OrderPlacedEvent
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderPlacedSagaConsumer(
    private val inventoryService: InventoryService,
    private val inventorySagaEventPublisher: InventorySagaEventPublisher,
    private val inventoryReservationRepository: InventoryReservationRepository,
    private val processedOrderEventRepository: ProcessedOrderEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @KafkaListener(
        id = "orderPlacedSagaConsumer",
        topics = ["\${app.kafka.topics.order-placed}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumeOrderPlaced(event: OrderPlacedEvent) {
        if (processedOrderEventRepository.existsById(event.eventId)) {
            logger.info("Ignoring duplicate OrderPlacedEvent eventId={} orderId={}", event.eventId, event.orderId)
            return
        }

        try {
            inventoryService.reserveInventory(event.skuCode, event.quantity)
            inventorySagaEventPublisher.publishReserved(
                InventoryReservedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    skuCode = event.skuCode,
                    reservedQuantity = event.quantity,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
            inventoryReservationRepository.save(
                InventoryReservationEntity(
                    orderId = event.orderId,
                    skuCode = event.skuCode,
                    reservedQuantity = event.quantity,
                    reservedAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        } catch (exception: InsufficientInventoryException) {
            inventorySagaEventPublisher.publishFailed(
                InventoryFailedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    skuCode = event.skuCode,
                    requestedQuantity = event.quantity,
                    reason = exception.message ?: "Insufficient inventory",
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        } catch (exception: InventoryNotFoundException) {
            inventorySagaEventPublisher.publishFailed(
                InventoryFailedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    skuCode = event.skuCode,
                    requestedQuantity = event.quantity,
                    reason = exception.message ?: "Inventory not found",
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        }

        processedOrderEventRepository.save(
            ProcessedOrderEventEntity(
                eventId = event.eventId,
                orderId = event.orderId,
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
        logger.info("Processed OrderPlacedEvent eventId={} orderId={} skuCode={}", event.eventId, event.orderId, event.skuCode)
    }
}
