package com.paulopacifico.inventoryservice.messaging.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.paulopacifico.inventoryservice.messaging.api.OrderPlacedEvent
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.util.backoff.FixedBackOff
import java.util.HashMap

@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaTopicProperties::class)
class InventoryKafkaConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean("kafkaObjectMapper")
    fun kafkaObjectMapper(
        objectMapperBuilder: Jackson2ObjectMapperBuilder,
    ): ObjectMapper =
        objectMapperBuilder
            .createXmlMapper(false)
            .build<ObjectMapper>()
            .registerKotlinModule()

    @Bean
    fun producerFactory(kafkaProperties: KafkaProperties): ProducerFactory<String, String> {
        val properties = HashMap(kafkaProperties.buildProducerProperties())
        properties.putIfAbsent(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        properties.putIfAbsent(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
        return DefaultKafkaProducerFactory(properties)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory)

    @Bean
    fun consumerFactory(
        kafkaProperties: KafkaProperties,
        @Qualifier("kafkaObjectMapper") kafkaObjectMapper: ObjectMapper,
    ): ConsumerFactory<String, OrderPlacedEvent> {
        val properties = HashMap(kafkaProperties.buildConsumerProperties())
        properties.putIfAbsent(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        properties.putIfAbsent(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer::class.java)

        val valueDeserializer = JsonDeserializer(OrderPlacedEvent::class.java, kafkaObjectMapper.copy(), false).apply {
            ignoreTypeHeaders()
            addTrustedPackages("*")
        }

        return DefaultKafkaConsumerFactory(properties, StringDeserializer(), valueDeserializer)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, OrderPlacedEvent>,
    ): ConcurrentKafkaListenerContainerFactory<String, OrderPlacedEvent> =
        ConcurrentKafkaListenerContainerFactory<String, OrderPlacedEvent>().apply {
            setConsumerFactory(consumerFactory)
            setCommonErrorHandler(kafkaErrorHandler())
        }

    @Bean
    fun kafkaErrorHandler(): DefaultErrorHandler =
        DefaultErrorHandler(
            { record, exception ->
                logger.error(
                    "Skipping Kafka record topic={} partition={} offset={} due to {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception.message,
                    exception,
                )
            },
            FixedBackOff(1_000L, 5L),
        ).apply {
            setRetryListeners(
                RetryListener { record, exception, deliveryAttempt ->
                    logger.warn(
                        "Retrying Kafka record topic={} partition={} offset={} attempt={} due to {}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        deliveryAttempt,
                        exception.message,
                    )
                },
            )
        }

    @Bean
    fun orderPlacedTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.orderPlaced).partitions(3).replicas(1).build()

    @Bean
    fun inventoryReservedTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.inventoryReserved).partitions(3).replicas(1).build()

    @Bean
    fun inventoryFailedTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.inventoryFailed).partitions(3).replicas(1).build()
}
