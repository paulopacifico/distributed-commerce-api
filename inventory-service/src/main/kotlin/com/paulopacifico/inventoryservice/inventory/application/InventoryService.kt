package com.paulopacifico.inventoryservice.inventory.application

import com.paulopacifico.inventoryservice.inventory.api.CreateInventoryRequest
import com.paulopacifico.inventoryservice.inventory.api.InventoryMapper
import com.paulopacifico.inventoryservice.inventory.api.InventoryReservationResponse
import com.paulopacifico.inventoryservice.inventory.api.InventoryResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository,
    private val inventoryMapper: InventoryMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createInventory(request: CreateInventoryRequest): InventoryResponse {
        val normalizedSkuCode = request.skuCode.trim()
        val existingInventory = inventoryRepository.findBySkuCode(normalizedSkuCode)
        if (existingInventory != null) {
            existingInventory.quantity += request.quantity
            logger.info(
                "Increased inventory for skuCode={} quantity={}",
                normalizedSkuCode,
                existingInventory.quantity,
            )
            return inventoryMapper.toResponse(existingInventory)
        }

        val inventory = inventoryMapper.toEntity(request.copy(skuCode = normalizedSkuCode))
        val savedInventory = inventoryRepository.save(inventory)
        logger.info(
            "Created inventory id={} skuCode={}",
            savedInventory.id,
            savedInventory.skuCode,
        )
        return inventoryMapper.toResponse(savedInventory)
    }

    @Transactional(readOnly = true)
    fun getBySkuCode(skuCode: String): InventoryResponse =
        inventoryRepository.findBySkuCode(skuCode.trim())
            ?.let(inventoryMapper::toResponse)
            ?: throw InventoryNotFoundException(skuCode)

    @Transactional
    fun releaseInventory(skuCode: String, quantity: Int) {
        val inventory = inventoryRepository.findBySkuCode(skuCode.trim())
            ?: throw InventoryNotFoundException(skuCode)
        inventory.quantity += quantity
        inventoryRepository.save(inventory)
        logger.info(
            "Released inventory for skuCode={} releasedQuantity={} newQuantity={}",
            inventory.skuCode,
            quantity,
            inventory.quantity,
        )
    }

    @Transactional
    fun reserveInventory(skuCode: String, requiredQuantity: Int): InventoryReservationResponse {
        val inventory = inventoryRepository.findBySkuCode(skuCode.trim())
            ?: throw InventoryNotFoundException(skuCode)

        if (!inventory.hasEnoughQuantity(requiredQuantity)) {
            throw InsufficientInventoryException(
                skuCode = inventory.skuCode,
                requestedQuantity = requiredQuantity,
                availableQuantity = inventory.quantity,
            )
        }

        inventory.deduct(requiredQuantity)
        inventoryRepository.save(inventory)
        logger.info(
            "Reserved inventory for skuCode={} requestedQuantity={} remainingQuantity={}",
            inventory.skuCode,
            requiredQuantity,
            inventory.quantity,
        )
        return inventoryMapper.toReservationResponse(inventory, requiredQuantity)
    }
}
