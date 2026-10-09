package com.orderflow.common.messaging;

/** Names shared by publishers and consumers. Kept in one place so a future service split has a single contract. */
public final class MessagingTopology {

    public static final String EVENTS_EXCHANGE = "orderflow.events";
    public static final String DEAD_LETTER_EXCHANGE = "orderflow.events.dlx";

    public static final String ORDER_CONFIRMED_ROUTING_KEY = "order.confirmed";
    public static final String ORDER_CONFIRMED_QUEUE = "notification.order-confirmed";
    public static final String ORDER_CONFIRMED_DLQ = "notification.order-confirmed.dlq";

    public static final String CORRELATION_HEADER = "X-Request-Id";

    private MessagingTopology() {
    }
}
