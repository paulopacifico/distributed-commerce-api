package com.paulopacifico.paymentservice.payment

import com.paulopacifico.paymentservice.messaging.api.OrderConfirmedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentFailedEvent
import com.paulopacifico.paymentservice.payment.application.PaymentRepository
import com.paulopacifico.paymentservice.payment.messaging.persistence.ProcessedOrderConfirmedEventRepository
import com.paulopacifico.paymentservice.support.AbstractIntegrationTest
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["payment.success-rate=0.0", "spring.kafka.consumer.group-id=payment-service-test-failure"])
class PaymentSagaFailureIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var processedOrderConfirmedEventRepository: ProcessedOrderConfirmedEventRepository
    @Autowired lateinit var kafkaTemplate: KafkaTemplate<String, String>
    @Autowired lateinit var kafkaListenerEndpointRegistry: KafkaListenerEndpointRegistry
    @Autowired @Qualifier("kafkaObjectMapper") lateinit var kafkaObjectMapper: com.fasterxml.jackson.databind.ObjectMapper

    init {
        beforeTest {
            paymentRepository.deleteAll()
            processedOrderConfirmedEventRepository.deleteAll()
        }

        "should publish PaymentFailedEvent and persist failure record when success rate is 0.0" {
            awaitTopicReady("order-confirmed-topic", "payment-succeeded-topic", "payment-failed-topic")

            kafkaConsumer("payment-service-it-failure").use { consumer ->
                consumer.subscribe(listOf("payment-succeeded-topic", "payment-failed-topic"))
                eventually(30.seconds) {
                    consumer.poll(Duration.ofMillis(200))
                    consumer.assignment().size shouldBeExactly 6
                }
                consumer.seekToEnd(consumer.assignment())

                ContainerTestUtils.waitForAssignment(
                    kafkaListenerEndpointRegistry.getListenerContainer("orderConfirmedSagaConsumer")!!,
                    3,
                )

                val event = OrderConfirmedEvent(
                    eventId = UUID.randomUUID(),
                    orderId = 701L,
                    orderNumber = "ORD-KT-701",
                    price = BigDecimal("30.00"),
                    quantity = 3,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                )

                kafkaTemplate.send(
                    ProducerRecord("order-confirmed-topic", event.orderNumber, kafkaObjectMapper.writeValueAsString(event)),
                ).get()

                val collectedRecords = mutableListOf<ConsumerRecord<String, String>>()

                eventually(30.seconds) {
                    consumer.poll(Duration.ofMillis(500)).forEach { collectedRecords.add(it) }

                    val payment = paymentRepository.findAll().firstOrNull { it.orderId == 701L }
                    requireNotNull(payment) { "Expected payment record for orderId=701" }
                    payment.status shouldBe "FAILED"
                    payment.failureReason shouldBe "Payment declined by payment provider"

                    val record = collectedRecords.firstOrNull { it.topic() == "payment-failed-topic" }
                    requireNotNull(record) { "Expected PaymentFailedEvent on payment-failed-topic" }
                    val failedEvent = kafkaObjectMapper.readValue(record.value(), PaymentFailedEvent::class.java)
                    failedEvent.orderId shouldBe 701L
                    failedEvent.reason shouldBe "Payment declined by payment provider"
                }
            }
        }
    }
}
