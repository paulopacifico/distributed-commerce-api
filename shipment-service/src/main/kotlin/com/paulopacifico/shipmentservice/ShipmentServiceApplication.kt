package com.paulopacifico.shipmentservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ShipmentServiceApplication

fun main(args: Array<String>) {
    runApplication<ShipmentServiceApplication>(*args)
}
