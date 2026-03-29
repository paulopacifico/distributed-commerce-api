package com.paulopacifico.orderservice.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;

import static org.springframework.kafka.config.TopicBuilder.name;

@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class OrderKafkaConfiguration {

    @Bean
    public ProducerFactory<String, String> producerFactory(KafkaProperties kafkaProperties) {
        var properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.putIfAbsent(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.putIfAbsent(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory(KafkaProperties kafkaProperties) {
        var properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.putIfAbsent(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.putIfAbsent(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            ObjectMapper objectMapper,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);

        var jackson2MessageConverter = new MappingJackson2MessageConverter();
        jackson2MessageConverter.setObjectMapper(objectMapper.copy());

        var recordMessageConverter = new StringJsonMessageConverter(objectMapper.copy());
        recordMessageConverter.setMessagingConverter(jackson2MessageConverter);
        factory.setRecordMessageConverter(recordMessageConverter);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
    }

    @Bean
    public NewTopic orderPlacedTopic(KafkaTopicProperties topics) {
        return name(topics.orderPlaced()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryReservedTopic(KafkaTopicProperties topics) {
        return name(topics.inventoryReserved()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryFailedTopic(KafkaTopicProperties topics) {
        return name(topics.inventoryFailed()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryReservedDltTopic(KafkaTopicProperties topics) {
        return name(topics.inventoryReserved() + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryFailedDltTopic(KafkaTopicProperties topics) {
        return name(topics.inventoryFailed() + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderFailedTopic(KafkaTopicProperties topics) {
        return name(topics.orderFailed()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderFailedDltTopic(KafkaTopicProperties topics) {
        return name(topics.orderFailed() + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderConfirmedTopic(KafkaTopicProperties topics) {
        return name(topics.orderConfirmed()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentSucceededTopic(KafkaTopicProperties topics) {
        return name(topics.paymentSucceeded()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentSucceededDltTopic(KafkaTopicProperties topics) {
        return name(topics.paymentSucceeded() + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic(KafkaTopicProperties topics) {
        return name(topics.paymentFailed()).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedDltTopic(KafkaTopicProperties topics) {
        return name(topics.paymentFailed() + ".DLT").partitions(3).replicas(1).build();
    }
}
