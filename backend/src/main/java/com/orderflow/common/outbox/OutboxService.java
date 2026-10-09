package com.orderflow.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.web.RequestIdFilter;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional outbox: the event row is written in the same transaction as the state change it describes, so
 * "order confirmed" and "event will be published" commit or roll back together, even if RabbitMQ is down.
 */
@Service
public class OutboxService {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** @param payloadFactory receives the event ID so consumers can de-duplicate on it */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String aggregateType, Object aggregateId, String eventType, String routingKey,
                        Function<UUID, Object> payloadFactory) {
        UUID eventId = UUID.randomUUID();
        try {
            String payload = objectMapper.writeValueAsString(payloadFactory.apply(eventId));
            outbox.save(new OutboxEvent(eventId, aggregateType, String.valueOf(aggregateId), eventType, routingKey, payload,
                    MDC.get(RequestIdFilter.MDC_KEY)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise event " + eventType, e);
        }
        return eventId;
    }
}
