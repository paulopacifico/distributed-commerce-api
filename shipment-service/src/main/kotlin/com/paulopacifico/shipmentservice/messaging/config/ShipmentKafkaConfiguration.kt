package com.paulopacifico.shipmentservice.messaging.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
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
import org.springframework.kafka.support.converter.StringJsonMessageConverter
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.util.backoff.FixedBackOff

@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaTopicProperties::class)
class ShipmentKafkaConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean("kafkaObjectMapper")
    fun kafkaObjectMapper(objectMapperBuilder: Jackson2ObjectMapperBuilder): ObjectMapper =
        objectMapperBuilder
            .createXmlMapper(false)
            .build<ObjectMapper>()
            .registerKotlinModule()

    @Bean
    fun producerFactory(kafkaProperties: KafkaProperties): ProducerFactory<String, String> {
        val properties = HashMap(kafkaProperties.buildProducerProperties(null))
        properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        return DefaultKafkaProducerFactory(properties)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> =
        KafkaTemplate(producerFactory)

    @Bean
    fun consumerFactory(kafkaProperties: KafkaProperties): ConsumerFactory<String, String> {
        val properties = HashMap(kafkaProperties.buildConsumerProperties(null))
        properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        return DefaultKafkaConsumerFactory(properties)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>,
        @Qualifier("kafkaObjectMapper") kafkaObjectMapper: ObjectMapper,
    ): ConcurrentKafkaListenerContainerFactory<String, String> =
        ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            setConsumerFactory(consumerFactory)
            val jackson2MessageConverter = MappingJackson2MessageConverter()
            jackson2MessageConverter.objectMapper = kafkaObjectMapper.copy()
            val recordMessageConverter = StringJsonMessageConverter(kafkaObjectMapper.copy())
            recordMessageConverter.setMessagingConverter(jackson2MessageConverter)
            setRecordMessageConverter(recordMessageConverter)
            setCommonErrorHandler(kafkaErrorHandler())
        }

    @Bean
    fun kafkaErrorHandler(): DefaultErrorHandler =
        DefaultErrorHandler(
            { record, exception ->
                logger.error(
                    "Skipping Kafka record topic={} partition={} offset={} due to {}",
                    record.topic(), record.partition(), record.offset(), exception.message, exception,
                )
            },
            FixedBackOff(1_000L, 5L),
        ).apply {
            setRetryListeners(
                RetryListener { record, exception, deliveryAttempt ->
                    logger.warn(
                        "Retrying Kafka record topic={} partition={} offset={} attempt={} due to {}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt, exception.message,
                    )
                },
            )
        }

    @Bean
    fun orderPaidTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.orderPaid).partitions(3).replicas(1).build()

    @Bean
    fun shipmentShippedTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.shipmentShipped).partitions(3).replicas(1).build()

    @Bean
    fun shipmentFailedTopic(topics: KafkaTopicProperties): NewTopic =
        TopicBuilder.name(topics.shipmentFailed).partitions(3).replicas(1).build()
}
