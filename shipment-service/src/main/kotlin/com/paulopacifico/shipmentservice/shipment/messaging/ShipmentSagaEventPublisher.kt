package com.paulopacifico.shipmentservice.shipment.messaging

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.paulopacifico.shipmentservice.messaging.api.ShipmentFailedEvent
import com.paulopacifico.shipmentservice.messaging.api.ShipmentShippedEvent
import com.paulopacifico.shipmentservice.messaging.config.KafkaTopicProperties
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ShipmentSagaEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("kafkaObjectMapper")
    private val kafkaObjectMapper: ObjectMapper,
    private val topics: KafkaTopicProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val sendTimeout: Duration = Duration.ofSeconds(10)

    fun publishShipped(event: ShipmentShippedEvent) {
        publish(topic = topics.shipmentShipped, key = event.orderNumber, event = event, eventName = "ShipmentShippedEvent")
    }

    fun publishFailed(event: ShipmentFailedEvent) {
        publish(topic = topics.shipmentFailed, key = event.orderNumber, event = event, eventName = "ShipmentFailedEvent")
    }

    private fun publish(topic: String, key: String, event: Any, eventName: String) {
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
