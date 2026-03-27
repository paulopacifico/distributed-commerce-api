package com.paulopacifico.orderservice.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulopacifico.orderservice.messaging.api.OrderFailedEvent;
import com.paulopacifico.orderservice.messaging.config.KafkaTopicProperties;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderFailedEventPublisher implements OrderFailedEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderFailedEventPublisher.class);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopicProperties topics;

    public KafkaOrderFailedEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            KafkaTopicProperties topics
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topics = topics;
    }

    @Override
    public void publish(OrderFailedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topics.orderFailed(), event.orderNumber(), payload)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.info(
                    "Published OrderFailedEvent eventId={} orderId={} orderNumber={} topic={}",
                    event.eventId(),
                    event.orderId(),
                    event.orderNumber(),
                    topics.orderFailed()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OrderFailedEvent for orderNumber=%s".formatted(event.orderNumber()), exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to publish OrderFailedEvent for orderNumber=%s".formatted(event.orderNumber()),
                    exception
            );
        }
    }
}
