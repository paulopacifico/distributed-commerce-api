package com.paulopacifico.shipmentservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ShipmentServiceApplication

fun main(args: Array<String>) {
    runApplication<ShipmentServiceApplication>(*args)
}
