package com.paulopacifico.inventoryservice.inventory.api

import com.paulopacifico.inventoryservice.inventory.application.InventoryService
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/inventories")
class InventoryController(
    private val inventoryService: InventoryService,
) {
    @PostMapping
    fun createInventory(
        @Valid @RequestBody request: CreateInventoryRequest,
    ): ResponseEntity<InventoryResponse> {
        val createdInventory = inventoryService.createInventory(request)
        return ResponseEntity
            .created(URI.create("/api/inventories/${createdInventory.skuCode}"))
            .body(createdInventory)
    }

    @GetMapping("/{skuCode}")
    fun getInventory(
        @PathVariable skuCode: String,
    ): InventoryResponse = inventoryService.getBySkuCode(skuCode)

    @PostMapping("/reservations")
    fun reserveInventory(
        @Valid @RequestBody request: ReserveInventoryRequest,
    ): InventoryReservationResponse = inventoryService.reserveInventory(
        skuCode = request.skuCode,
        requiredQuantity = request.quantity,
    )
}
