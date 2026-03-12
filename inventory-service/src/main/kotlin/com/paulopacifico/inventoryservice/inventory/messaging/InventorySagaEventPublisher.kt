package com.paulopacifico.inventoryservice.inventory.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.paulopacifico.inventoryservice.messaging.api.InventoryFailedEvent
import com.paulopacifico.inventoryservice.messaging.api.InventoryReservedEvent
import com.paulopacifico.inventoryservice.messaging.config.KafkaTopicProperties
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class InventorySagaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val kafkaObjectMapper: ObjectMapper,
    private val topics: KafkaTopicProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun publishReserved(event: InventoryReservedEvent) {
        publish(
            topic = topics.inventoryReserved,
            key = event.orderNumber,
            event = event,
            eventName = "InventoryReservedEvent",
        )
    }

    fun publishFailed(event: InventoryFailedEvent) {
        publish(
            topic = topics.inventoryFailed,
            key = event.orderNumber,
            event = event,
            eventName = "InventoryFailedEvent",
        )
    }

    private fun publish(
        topic: String,
        key: String,
        event: Any,
        eventName: String,
    ) {
        try {
            val payload = kafkaObjectMapper.writeValueAsString(event)
            kafkaTemplate.send(topic, key, payload)
            logger.info("Published {} topic={} key={}", eventName, topic, key)
        } catch (exception: JsonProcessingException) {
            throw IllegalStateException("Failed to serialize $eventName for key=$key", exception)
        }
    }
}
