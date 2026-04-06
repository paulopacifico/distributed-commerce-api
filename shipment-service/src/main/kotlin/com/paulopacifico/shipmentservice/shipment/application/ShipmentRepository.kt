package com.paulopacifico.shipmentservice.shipment.application

import com.paulopacifico.shipmentservice.shipment.domain.ShipmentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ShipmentRepository : JpaRepository<ShipmentEntity, Long>
