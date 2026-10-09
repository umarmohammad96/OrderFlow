package com.orderflow.notification.consumer;

import com.orderflow.common.messaging.MessagingTopology;
import com.orderflow.common.web.RequestIdFilter;
import com.orderflow.notification.service.NotificationService;
import com.orderflow.payment.dto.OrderConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** Notification worker: runs on listener threads, fully decoupled from the HTTP request that confirmed the order. */
@Component
public class OrderConfirmedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmedConsumer.class);

    private final NotificationService notificationService;

    public OrderConfirmedConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = MessagingTopology.ORDER_CONFIRMED_QUEUE)
    public void onOrderConfirmed(OrderConfirmedEvent event,
                                 @Header(name = MessagingTopology.CORRELATION_HEADER, required = false) String requestId) {
        if (requestId != null) {
            MDC.put(RequestIdFilter.MDC_KEY, requestId);
        }
        try {
            log.info("Received OrderConfirmedEvent {} for order {}", event.eventId(), event.orderId());
            notificationService.handleOrderConfirmed(event);
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }
}
