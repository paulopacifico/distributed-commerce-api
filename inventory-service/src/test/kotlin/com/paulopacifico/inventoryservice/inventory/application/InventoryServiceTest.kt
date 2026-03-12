package com.paulopacifico.inventoryservice.inventory.application

import com.paulopacifico.inventoryservice.inventory.api.CreateInventoryRequest
import com.paulopacifico.inventoryservice.inventory.api.InventoryMapper
import com.paulopacifico.inventoryservice.inventory.domain.InventoryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class InventoryServiceTest {

    private lateinit var repository: InventoryRepository
    private lateinit var service: InventoryService

    @BeforeEach
    fun setUp() {
        repository = mock(InventoryRepository::class.java)
        service = InventoryService(repository, InventoryMapper())
    }

    @Test
    fun shouldCreateInventoryWhenSkuCodeDoesNotExist() {
        val request = CreateInventoryRequest(skuCode = "SKU-100", quantity = 10)
        `when`(repository.findBySkuCode("SKU-100")).thenReturn(null)
        `when`(repository.save(any(InventoryEntity::class.java))).thenAnswer { invocation ->
            val entity = invocation.getArgument<InventoryEntity>(0)
            entity.id = 1L
            entity
        }

        val response = service.createInventory(request)

        assertEquals(1L, response.id)
        assertEquals("SKU-100", response.skuCode)
        assertEquals(10, response.quantity)
        verify(repository, times(1)).save(any(InventoryEntity::class.java))
    }

    @Test
    fun shouldDeductInventoryWhenEnoughStockIsAvailable() {
        val inventory = InventoryEntity(id = 1L, skuCode = "SKU-200", quantity = 8)
        `when`(repository.findBySkuCode("SKU-200")).thenReturn(inventory)

        val response = service.reserveInventory("SKU-200", 3)

        assertEquals("SKU-200", response.skuCode)
        assertEquals(3, response.requestedQuantity)
        assertEquals(5, response.remainingQuantity)
        assertEquals(true, response.reserved)
        assertEquals(5, inventory.quantity)
    }

    @Test
    fun shouldThrowWhenInventoryIsInsufficient() {
        val inventory = InventoryEntity(id = 1L, skuCode = "SKU-300", quantity = 2)
        `when`(repository.findBySkuCode("SKU-300")).thenReturn(inventory)

        val exception = assertThrows(InsufficientInventoryException::class.java) {
            service.reserveInventory("SKU-300", 5)
        }

        assertEquals(
            "Insufficient inventory for skuCode 'SKU-300'. Requested=5, available=2",
            exception.message,
        )
    }
}
