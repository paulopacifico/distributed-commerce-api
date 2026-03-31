package com.paulopacifico.paymentservice.payment

import com.paulopacifico.paymentservice.messaging.api.OrderConfirmedEvent
import com.paulopacifico.paymentservice.messaging.api.PaymentSucceededEvent
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

@TestPropertySource(properties = ["payment.success-rate=1.0"])
class PaymentSagaIntegrationTest : AbstractIntegrationTest() {

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

        "should process payment and publish PaymentSucceededEvent when success rate is 1.0" {
            awaitTopicReady("order-confirmed-topic", "payment-succeeded-topic", "payment-failed-topic")

            kafkaConsumer("payment-service-it-success").use { consumer ->
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
                    orderId = 501L,
                    orderNumber = "ORD-KT-501",
                    price = BigDecimal("25.00"),
                    quantity = 2,
                    occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
                )

                kafkaTemplate.send(
                    ProducerRecord("order-confirmed-topic", event.orderNumber, kafkaObjectMapper.writeValueAsString(event)),
                ).get()

                val collectedRecords = mutableListOf<ConsumerRecord<String, String>>()

                eventually(30.seconds) {
                    consumer.poll(Duration.ofMillis(500)).forEach { collectedRecords.add(it) }

                    val payment = paymentRepository.findAll().firstOrNull { it.orderId == 501L }
                    requireNotNull(payment) { "Expected payment record for orderId=501" }
                    payment.status shouldBe "SUCCEEDED"
                    payment.amount shouldBe BigDecimal("50.00")

                    val record = collectedRecords.firstOrNull { it.topic() == "payment-succeeded-topic" }
                    requireNotNull(record) { "Expected PaymentSucceededEvent on payment-succeeded-topic" }
                    val succeededEvent = kafkaObjectMapper.readValue(record.value(), PaymentSucceededEvent::class.java)
                    succeededEvent.orderId shouldBe 501L
                    succeededEvent.amount shouldBe BigDecimal("50.00")
                }
            }
        }

        "should be idempotent when same OrderConfirmedEvent is delivered twice" {
            awaitTopicReady("order-confirmed-topic")

            ContainerTestUtils.waitForAssignment(
                kafkaListenerEndpointRegistry.getListenerContainer("orderConfirmedSagaConsumer")!!,
                3,
            )

            val eventId = UUID.randomUUID()
            val event = OrderConfirmedEvent(
                eventId = eventId,
                orderId = 601L,
                orderNumber = "ORD-KT-601",
                price = BigDecimal("10.00"),
                quantity = 1,
                occurredAt = OffsetDateTime.now(ZoneOffset.UTC),
            )
            val payload = kafkaObjectMapper.writeValueAsString(event)

            kafkaTemplate.send(ProducerRecord("order-confirmed-topic", event.orderNumber, payload)).get()
            kafkaTemplate.send(ProducerRecord("order-confirmed-topic", event.orderNumber, payload)).get()

            eventually(30.seconds) {
                val payments = paymentRepository.findAll().filter { it.orderId == 601L }
                payments.size shouldBeExactly 1
            }
        }
    }
}
