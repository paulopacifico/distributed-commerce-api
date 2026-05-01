package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.messaging.api.ShipmentFailedEvent;
import com.paulopacifico.orderservice.messaging.api.ShipmentShippedEvent;
import com.paulopacifico.orderservice.order.application.OrderSagaService;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedShipmentEventEntity;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedShipmentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
public class ShipmentSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShipmentSagaConsumer.class);

    private final OrderSagaService orderSagaService;
    private final ProcessedShipmentEventRepository processedShipmentEventRepository;

    public ShipmentSagaConsumer(
            OrderSagaService orderSagaService,
            ProcessedShipmentEventRepository processedShipmentEventRepository
    ) {
        this.orderSagaService = orderSagaService;
        this.processedShipmentEventRepository = processedShipmentEventRepository;
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.shipment-shipped}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeShipmentShipped(ShipmentShippedEvent event) {
        if (processedShipmentEventRepository.existsById(event.eventId())) {
            log.info("Ignoring duplicate ShipmentShippedEvent eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }
        orderSagaService.markAsShipped(event.orderId());
        processedShipmentEventRepository.save(
                new ProcessedShipmentEventEntity(event.eventId(), event.orderId(), OffsetDateTime.now())
        );
        log.info("Processed ShipmentShippedEvent eventId={} orderId={}", event.eventId(), event.orderId());
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.shipment-failed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeShipmentFailed(ShipmentFailedEvent event) {
        if (processedShipmentEventRepository.existsById(event.eventId())) {
            log.info("Ignoring duplicate ShipmentFailedEvent eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }
        orderSagaService.markAsShipmentFailed(event.orderId(), event.reason());
        processedShipmentEventRepository.save(
                new ProcessedShipmentEventEntity(event.eventId(), event.orderId(), OffsetDateTime.now())
        );
        log.info("Processed ShipmentFailedEvent eventId={} orderId={} reason={}", event.eventId(), event.orderId(), event.reason());
    }
}
