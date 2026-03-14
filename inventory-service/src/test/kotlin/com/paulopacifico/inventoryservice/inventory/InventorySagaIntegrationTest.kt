package com.paulopacifico.inventoryservice.inventory

import com.fasterxml.jackson.databind.ObjectMapper
import com.paulopacifico.inventoryservice.inventory.application.InventoryRepository
import com.paulopacifico.inventoryservice.inventory.domain.InventoryEntity
import com.paulopacifico.inventoryservice.inventory.messaging.persistence.ProcessedOrderEventRepository
import com.paulopacifico.inventoryservice.messaging.api.InventoryReservedEvent
import com.paulopacifico.inventoryservice.messaging.api.OrderPlacedEvent
import com.paulopacifico.inventoryservice.support.AbstractIntegrationTest
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils

class InventorySagaIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var inventoryRepository: InventoryRepository

    @Autowired
    lateinit var processedOrderEventRepository: ProcessedOrderEventRepository

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry

    @Autowired
    @Qualifier("kafkaObjectMapper")
    lateinit var kafkaObjectMapper: ObjectMapper

    init {
        beforeSpec {
            inventoryRepository.deleteAll()
            processedOrderEventRepository.deleteAll()
        }

        beforeTest {
            inventoryRepository.deleteAll()
            processedOrderEventRepository.deleteAll()
        }

        "should consume order placed event, deduct stock, and publish inventory reserved event" {
            awaitTopicReady("order-placed-topic", "inventory-reserved-topic", "inventory-failed-topic")

            inventoryRepository.save(
                InventoryEntity(
                    skuCode = "SKU-KT-100",
                    quantity = 12,
                ),
            )

            val publisherProbe = mockk<(InventoryReservedEvent) -> Unit>(relaxed = true)

            kafkaConsumer("inventory-service-it").use { consumer ->
                consumer.subscribe(listOf("inventory-reserved-topic"))
                eventually(30.seconds) {
                    consumer.poll(Duration.ofMillis(200))
                    consumer.assignment().size shouldBeExactly 3
                }
                consumer.seekToEnd(consumer.assignment())

                val listenerContainer = requireNotNull(
                    kafkaListenerEndpointRegistry.getListenerContainer("orderPlacedSagaConsumer"),
                ) {
                    "Kafka listener container orderPlacedSagaConsumer was not registered"
                }
                ContainerTestUtils.waitForAssignment(listenerContainer, 3)

                val orderPlacedEvent = OrderPlacedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = 101L,
                    orderNumber = "ORD-KT-101",
                    skuCode = "SKU-KT-100",
                    price = BigDecimal("15.50"),
                    quantity = 4,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                )

                kafkaTemplate.send(
                    ProducerRecord(
                        "order-placed-topic",
                        orderPlacedEvent.orderNumber,
                        kafkaObjectMapper.writeValueAsString(orderPlacedEvent),
                    ),
                ).get()

                eventually(30.seconds) {
                    val updatedInventory = requireNotNull(inventoryRepository.findBySkuCode("SKU-KT-100"))
                    updatedInventory.quantity shouldBeExactly 8

                    val records = consumer.poll(java.time.Duration.ofMillis(500))
                    records.count() shouldBe 1

                    val payload = records.iterator().next().value()
                    val event = kafkaObjectMapper.readValue(payload, InventoryReservedEvent::class.java)

                    event.orderId shouldBe 101L
                    event.skuCode shouldBe "SKU-KT-100"
                    event.reservedQuantity shouldBeExactly 4

                    publisherProbe(event)
                    verify(exactly = 1) {
                        publisherProbe.invoke(
                            match {
                                it.orderNumber == "ORD-KT-101" && it.reservedQuantity == 4
                            },
                        )
                    }
                }
            }
        }
    }
}
