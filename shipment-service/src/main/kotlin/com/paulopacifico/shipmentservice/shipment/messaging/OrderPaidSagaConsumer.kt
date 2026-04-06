package com.paulopacifico.shipmentservice.shipment.messaging

import com.paulopacifico.shipmentservice.messaging.api.OrderPaidEvent
import com.paulopacifico.shipmentservice.messaging.api.ShipmentFailedEvent
import com.paulopacifico.shipmentservice.messaging.api.ShipmentShippedEvent
import com.paulopacifico.shipmentservice.shipment.application.ShipmentService
import com.paulopacifico.shipmentservice.shipment.messaging.persistence.ProcessedOrderPaidEventEntity
import com.paulopacifico.shipmentservice.shipment.messaging.persistence.ProcessedOrderPaidEventRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderPaidSagaConsumer(
    private val shipmentService: ShipmentService,
    private val shipmentSagaEventPublisher: ShipmentSagaEventPublisher,
    private val processedOrderPaidEventRepository: ProcessedOrderPaidEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    @KafkaListener(
        id = "orderPaidSagaConsumer",
        topics = ["\${app.kafka.topics.order-paid}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
    )
    fun consumeOrderPaid(event: OrderPaidEvent) {
        if (processedOrderPaidEventRepository.existsById(event.eventId)) {
            logger.info("Ignoring duplicate OrderPaidEvent eventId={} orderId={}", event.eventId, event.orderId)
            return
        }

        val shipment = shipmentService.processShipment(event)

        if (shipment.status == "SHIPPED") {
            shipmentSagaEventPublisher.publishShipped(
                ShipmentShippedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        } else {
            shipmentSagaEventPublisher.publishFailed(
                ShipmentFailedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = event.orderId,
                    orderNumber = event.orderNumber,
                    reason = shipment.failureReason ?: "Unknown shipment failure",
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                ),
            )
        }

        processedOrderPaidEventRepository.save(
            ProcessedOrderPaidEventEntity(
                eventId = event.eventId,
                orderId = event.orderId,
                processedAt = OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
        logger.info("Processed OrderPaidEvent eventId={} orderId={} shipmentStatus={}", event.eventId, event.orderId, shipment.status)
    }
}
