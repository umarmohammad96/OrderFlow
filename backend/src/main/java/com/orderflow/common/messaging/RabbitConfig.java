package com.orderflow.common.messaging;

import static com.orderflow.common.messaging.MessagingTopology.DEAD_LETTER_EXCHANGE;
import static com.orderflow.common.messaging.MessagingTopology.EVENTS_EXCHANGE;
import static com.orderflow.common.messaging.MessagingTopology.ORDER_CONFIRMED_DLQ;
import static com.orderflow.common.messaging.MessagingTopology.ORDER_CONFIRMED_QUEUE;
import static com.orderflow.common.messaging.MessagingTopology.ORDER_CONFIRMED_ROUTING_KEY;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topology: topic exchange → durable notification queue. Messages that still fail after the listener's retries
 * are dead-lettered to a DLQ for inspection instead of being lost or redelivered forever.
 */
@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue orderConfirmedQueue() {
        return QueueBuilder.durable(ORDER_CONFIRMED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(ORDER_CONFIRMED_DLQ)
                .build();
    }

    @Bean
    Queue orderConfirmedDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_CONFIRMED_DLQ).build();
    }

    @Bean
    Binding orderConfirmedBinding() {
        return BindingBuilder.bind(orderConfirmedQueue()).to(eventsExchange()).with(ORDER_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    Binding orderConfirmedDeadLetterBinding() {
        return BindingBuilder.bind(orderConfirmedDeadLetterQueue()).to(deadLetterExchange()).with(ORDER_CONFIRMED_DLQ);
    }

    /** JSON payloads; the consumer infers the target type from the listener signature, not from a Java class header. */
    @Bean
    MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
