package com.paulopacifico.orderservice.order.messaging;

import com.paulopacifico.orderservice.messaging.api.PaymentFailedEvent;
import com.paulopacifico.orderservice.messaging.api.PaymentSucceededEvent;
import com.paulopacifico.orderservice.order.application.OrderSagaService;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedPaymentEventEntity;
import com.paulopacifico.orderservice.order.messaging.persistence.ProcessedPaymentEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
public class PaymentSagaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaConsumer.class);

    private final OrderSagaService orderSagaService;
    private final ProcessedPaymentEventRepository processedPaymentEventRepository;

    public PaymentSagaConsumer(
            OrderSagaService orderSagaService,
            ProcessedPaymentEventRepository processedPaymentEventRepository
    ) {
        this.orderSagaService = orderSagaService;
        this.processedPaymentEventRepository = processedPaymentEventRepository;
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.payment-succeeded}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentSucceeded(PaymentSucceededEvent event) {
        if (processedPaymentEventRepository.existsById(event.eventId())) {
            log.info("Ignoring duplicate PaymentSucceededEvent eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }
        orderSagaService.markAsPaid(event.orderId());
        processedPaymentEventRepository.save(
                new ProcessedPaymentEventEntity(event.eventId(), event.orderId(), OffsetDateTime.now())
        );
        log.info("Processed PaymentSucceededEvent eventId={} orderId={}", event.eventId(), event.orderId());
    }

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.payment-failed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentFailed(PaymentFailedEvent event) {
        if (processedPaymentEventRepository.existsById(event.eventId())) {
            log.info("Ignoring duplicate PaymentFailedEvent eventId={} orderId={}", event.eventId(), event.orderId());
            return;
        }
        orderSagaService.markAsPaymentFailed(event.orderId());
        processedPaymentEventRepository.save(
                new ProcessedPaymentEventEntity(event.eventId(), event.orderId(), OffsetDateTime.now())
        );
        log.info("Processed PaymentFailedEvent eventId={} orderId={} reason={}", event.eventId(), event.orderId(), event.reason());
    }
}
