package com.paulopacifico.shipmentservice.shipment.application

import com.paulopacifico.shipmentservice.messaging.api.OrderPaidEvent
import com.paulopacifico.shipmentservice.shipment.domain.ShipmentEntity
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ShipmentService(
    private val shipmentRepository: ShipmentRepository,
    private val shipmentProperties: ShipmentProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun processShipment(event: OrderPaidEvent): ShipmentEntity {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val shipment = shipmentRepository.save(
            ShipmentEntity(
                orderId = event.orderId,
                orderNumber = event.orderNumber,
                status = "PROCESSING",
                createdAt = now,
                updatedAt = now,
            ),
        )

        val succeeded = Math.random() < shipmentProperties.successRate
        if (succeeded) {
            shipment.status = "SHIPPED"
        } else {
            shipment.status = "FAILED"
            shipment.failureReason = "Shipment carrier rejected the order"
        }
        shipment.updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val saved = shipmentRepository.save(shipment)
        logger.info("Processed shipment orderId={} status={}", event.orderId, saved.status)
        return saved
    }
}
