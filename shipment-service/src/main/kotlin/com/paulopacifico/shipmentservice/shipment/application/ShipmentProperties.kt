package com.paulopacifico.shipmentservice.shipment.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("shipment")
data class ShipmentProperties(
    var successRate: Double = 1.0,
)
