package com.orderflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.common.messaging.MessagingTopology;
import com.orderflow.support.IntegrationTestBase;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;

/** Payments (mock gateway, retry safety) and the asynchronous OrderConfirmedEvent → notification flow. */
class PaymentAndNotificationIT extends IntegrationTestBase {

    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    RabbitAdmin rabbitAdmin;
    @Autowired
    RabbitListenerEndpointRegistry listeners;

    TestUser customer;
    long product;

    @BeforeEach
    void setUp() throws Exception {
        rabbitAdmin.purgeQueue(MessagingTopology.ORDER_CONFIRMED_DLQ, false);
        customer = registerCustomer();
        product = createProduct("Headphones", "199.99", 5);
    }

    @Test
    void successfulPaymentConfirmsOrderAndNotificationsArriveAsynchronously() throws Exception {
        long orderId = placeOrderOk(customer, product, 2);

        pay(customer, orderId, "tok_visa")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.amount").value(399.98))
                .andExpect(jsonPath("$.data.transactionReference").isNotEmpty());

        // reservation converted into a sale
        assertThat(available(product)).isEqualTo(3);
        assertThat(reserved(product)).isZero();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationChannels(orderId)).containsExactlyInAnyOrder("EMAIL", "SMS"));
        getAs(customer, "/api/notifications")
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[?(@.channel == 'EMAIL')].recipient").value(customer.email()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void declinedPaymentCanBeRetried() throws Exception {
        long orderId = placeOrderOk(customer, product, 1);

        pay(customer, orderId, "tok_declined")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.orderStatus").value("PAYMENT_PENDING"));
        pay(customer, orderId, "tok_gateway_error")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.data.failureReason").value("Payment provider error: Payment provider unavailable"));
        assertThat(reserved(product)).isEqualTo(1); // stock stays held while payment is retried

        pay(customer, orderId, "tok_visa").andExpect(status().isCreated());
        getAs(customer, "/api/payments/" + orderId)
                .andExpect(jsonPath("$.data[*].status").value(org.hamcrest.Matchers.contains("SUCCEEDED", "FAILED", "FAILED")));
    }

    @Test
    void repeatedPaymentIsReplayedNotChargedTwice() throws Exception {
        long orderId = placeOrderOk(customer, product, 1);
        String reference = data(pay(customer, orderId, "tok_visa").andExpect(status().isCreated()))
                .get("transactionReference").asText();

        pay(customer, orderId, "tok_visa")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionReference").value(reference));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE status = 'SUCCEEDED'", Integer.class)).isEqualTo(1);
        assertThat(count("outbox_events")).isEqualTo(1);
        assertThat(available(product)).isEqualTo(4);
    }

    @Test
    void concurrentPaymentAttemptsChargeExactlyOnce() throws Exception {
        long orderId = placeOrderOk(customer, product, 1);
        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    go.await();
                    return httpPost(customer, "/api/payments/" + orderId, "{\"paymentToken\":\"tok_visa\"}");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, pool));
        }
        go.countDown();
        Map<Integer, Long> statuses = futures.stream().map(CompletableFuture::join)
                .collect(Collectors.groupingBy(HttpResponse::statusCode, Collectors.counting()));
        pool.shutdown();

        assertThat(statuses.get(201)).isEqualTo(1L);
        assertThat(statuses.keySet()).isSubsetOf(201, 200, 409); // 409 = PAYMENT_IN_PROGRESS, 200 = already paid
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE status = 'SUCCEEDED'", Integer.class)).isEqualTo(1);
        assertThat(count("outbox_events")).isEqualTo(1);
    }

    @Test
    void cancelledOrForeignOrdersCannotBePaid() throws Exception {
        long orderId = placeOrderOk(customer, product, 1);
        TestUser stranger = registerCustomer();
        pay(stranger, orderId, "tok_visa").andExpect(status().isNotFound());

        postAs(customer, "/api/orders/" + orderId + "/cancel", "").andExpect(status().isOk());
        pay(customer, orderId, "tok_visa").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_CANCELLED"));
        assertThat(count("payments")).isZero();
    }

    @Test
    void orderConfirmationDoesNotWaitForTheNotificationConsumer() throws Exception {
        long orderId = placeOrderOk(customer, product, 1);
        listeners.stop(); // the notification worker is down
        try {
            pay(customer, orderId, "tok_visa").andExpect(status().isCreated());

            // The event reaches the broker and waits in the durable queue...
            await().atMost(Duration.ofSeconds(10)).until(() -> queueDepth(MessagingTopology.ORDER_CONFIRMED_QUEUE) == 1);
            assertThat(notificationChannels(orderId)).isEmpty();
            getAs(customer, "/api/orders/" + orderId).andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        } finally {
            listeners.start();
        }
        // ...and is processed once the worker is back.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationChannels(orderId)).containsExactlyInAnyOrder("EMAIL", "SMS"));
    }

    @Test
    void redeliveredEventDoesNotNotifyTwice() throws Exception {
        long orderId = placeOrderOk(customer, product, 1);
        pay(customer, orderId, "tok_visa");
        await().atMost(Duration.ofSeconds(10)).until(() -> notificationChannels(orderId).size() == 2);

        String payload = jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class);
        publishRaw(payload);
        publishRaw(payload);

        // give the consumer time to process both duplicates, then verify nothing new was sent
        await().pollDelay(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5))
                .until(() -> queueDepth(MessagingTopology.ORDER_CONFIRMED_QUEUE) == 0);
        assertThat(notificationChannels(orderId)).hasSize(2);
    }

    @Test
    void unprocessableMessagesAreDeadLettered() throws Exception {
        publishRaw("this is not json");
        // valid JSON whose processing fails (missing eventId violates NOT NULL) is retried, then dead-lettered
        publishRaw("{\"orderId\":1,\"userId\":1,\"customerEmail\":\"x@example.com\",\"totalAmount\":1}");

        await().atMost(Duration.ofSeconds(15)).until(() -> queueDepth(MessagingTopology.ORDER_CONFIRMED_DLQ) == 2);
        assertThat(count("notifications")).isZero();
    }

    private List<String> notificationChannels(long orderId) {
        return jdbc.queryForList("SELECT channel FROM notifications WHERE order_id = ?", String.class, orderId);
    }

    private int queueDepth(String queue) {
        var info = rabbitAdmin.getQueueInfo(queue);
        return info == null ? -1 : info.getMessageCount();
    }

    private void publishRaw(String body) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message message = MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).andProperties(props).build();
        rabbitTemplate.send(MessagingTopology.EVENTS_EXCHANGE, MessagingTopology.ORDER_CONFIRMED_ROUTING_KEY, message);
    }
}
