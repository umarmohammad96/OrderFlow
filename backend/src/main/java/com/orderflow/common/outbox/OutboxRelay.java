package com.orderflow.common.outbox;

import com.orderflow.common.messaging.MessagingTopology;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the outbox and publishes pending events with publisher confirms. Delivery is at-least-once (a crash
 * after publish but before commit re-sends), which is why consumers de-duplicate on the event ID.
 */
@Component
@ConditionalOnProperty(name = "orderflow.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final OutboxRepository outbox;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outbox, RabbitTemplate rabbitTemplate, TransactionTemplate transactionTemplate,
                       @Value("${orderflow.outbox.batch-size}") int batchSize) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orderflow.outbox.poll-interval}")
    public void relay() {
        Integer published = transactionTemplate.execute(status -> {
            List<OutboxEvent> batch = outbox.lockUnpublished(batchSize);
            int sent = 0;
            for (OutboxEvent event : batch) {
                try {
                    publish(event);
                    event.markPublished();
                    sent++;
                } catch (RuntimeException e) {
                    event.markFailed(e.getMessage());
                    log.warn("Outbox publish failed for event {} (attempt {}): {}", event.getId(), event.getAttempts(),
                            e.getMessage());
                    break; // broker likely unavailable: keep ordering, retry the rest on the next poll
                }
            }
            return sent;
        });
        if (published != null && published > 0) {
            log.debug("Outbox relay published {} event(s)", published);
        }
    }

    private void publish(OutboxEvent event) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding(StandardCharsets.UTF_8.name());
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setMessageId(event.getId().toString());
        props.setType(event.getEventType());
        if (event.getCorrelationId() != null) {
            props.setHeader(MessagingTopology.CORRELATION_HEADER, event.getCorrelationId());
        }
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .andProperties(props).build();
        rabbitTemplate.invoke(ops -> {
            ops.send(MessagingTopology.EVENTS_EXCHANGE, event.getRoutingKey(), message);
            ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
        log.info("Published {} {} for aggregate {}", event.getEventType(), event.getId(), event.getAggregateId());
    }
}
