package com.paulopacifico.inventoryservice.inventory.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.paulopacifico.inventoryservice.messaging.api.InventoryFailedEvent
import com.paulopacifico.inventoryservice.messaging.api.InventoryReservedEvent
import com.paulopacifico.inventoryservice.messaging.config.KafkaTopicProperties
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class InventorySagaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("kafkaObjectMapper")
    private val kafkaObjectMapper: ObjectMapper,
    private val topics: KafkaTopicProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val sendTimeout: Duration = Duration.ofSeconds(10)

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
            kafkaTemplate.send(topic, key, payload).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
            logger.info("Published {} topic={} key={}", eventName, topic, key)
        } catch (exception: JsonProcessingException) {
            throw IllegalStateException("Failed to serialize $eventName for key=$key", exception)
        } catch (exception: Exception) {
            throw IllegalStateException("Failed to publish $eventName for key=$key", exception)
        }
    }
}
