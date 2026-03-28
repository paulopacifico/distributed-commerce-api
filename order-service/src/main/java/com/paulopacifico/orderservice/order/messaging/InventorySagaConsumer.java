package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.messaging.api.InventoryFailedEvent;
import com.paulopacifico.orderservice.messaging.api.InventoryReservedEvent;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedInventoryEventEntity;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedInventoryEventRepository;
import com.paulopacifico.orderservice.order.application.OrderSagaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
public class InventorySagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventorySagaConsumer.class);

    private final OrderSagaService orderSagaService;
    private final ProcessedInventoryEventRepository processedInventoryEventRepository;

    public InventorySagaConsumer(
            OrderSagaService orderSagaService,
            ProcessedInventoryEventRepository processedInventoryEventRepository
    ) {
        this.orderSagaService = orderSagaService;
        this.processedInventoryEventRepository = processedInventoryEventRepository;
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.inventory-reserved}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeInventoryReserved(InventoryReservedEvent event) {
        if (processedInventoryEventRepository.existsById(event.eventId())) {
            log.info("Ignoring duplicate InventoryReservedEvent eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }

        orderSagaService.confirmOrder(event.orderId());
        processedInventoryEventRepository.save(
                new ProcessedInventoryEventEntity(
                        event.eventId(),
                        event.orderId(),
                        OffsetDateTime.now()
                )
        );
        log.info("Processed InventoryReservedEvent eventId={} orderId={}", event.eventId(), event.orderId());
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.inventory-failed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeInventoryFailed(InventoryFailedEvent event) {
        if (processedInventoryEventRepository.existsById(event.eventId())) {
            log.info("Ignoring duplicate InventoryFailedEvent eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }

        orderSagaService.failOrder(event.orderId(), event.reason());
        processedInventoryEventRepository.save(
                new ProcessedInventoryEventEntity(
                        event.eventId(),
                        event.orderId(),
                        OffsetDateTime.now()
                )
        );
        log.info("Processed InventoryFailedEvent eventId={} orderId={} reason={}", event.eventId(), event.orderId(), event.reason());
    }
}
