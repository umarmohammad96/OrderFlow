package com.orderflow.notification.service;

import com.orderflow.notification.domain.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Mock email/SMS provider: logs instead of sending. Swap for SES/Twilio adapters in production. */
@Component
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    public void send(NotificationChannel channel, String recipient, String message) {
        log.info("[MOCK-{}] to={} message=\"{}\"", channel, recipient, message);
    }
}
