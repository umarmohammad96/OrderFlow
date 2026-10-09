package com.orderflow.notification.service;

import com.orderflow.notification.domain.NotificationChannel;
import com.orderflow.notification.dto.NotificationResponse;
import com.orderflow.notification.repository.NotificationRepository;
import com.orderflow.payment.dto.OrderConfirmedEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer logic. RabbitMQ delivery is at-least-once, so the same event can arrive twice; the
 * (event_id, channel) unique key turns a duplicate into a no-op instead of a second email/SMS.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JdbcTemplate jdbc;
    private final NotificationSender sender;
    private final NotificationRepository notifications;

    public NotificationService(JdbcTemplate jdbc, NotificationSender sender, NotificationRepository notifications) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.notifications = notifications;
    }

    @Transactional
    public int handleOrderConfirmed(OrderConfirmedEvent event) {
        String email = "Hi %s, your order #%d (%s) is confirmed. Payment ref %s."
                .formatted(event.customerName(), event.orderId(), event.totalAmount(), event.paymentReference());
        String sms = "OrderFlow: order #%d confirmed, total %s.".formatted(event.orderId(), event.totalAmount());

        int sent = 0;
        sent += deliver(event, NotificationChannel.EMAIL, event.customerEmail(), email);
        sent += deliver(event, NotificationChannel.SMS, "user-" + event.userId(), sms);
        if (sent == 0) {
            log.info("Duplicate OrderConfirmedEvent {} for order {} ignored", event.eventId(), event.orderId());
        }
        return sent;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> forUser(Long userId) {
        return notifications.findTop50ByUserIdOrderByIdDesc(userId).stream().map(NotificationResponse::from).toList();
    }

    private int deliver(OrderConfirmedEvent event, NotificationChannel channel, String recipient, String message) {
        int inserted = jdbc.update("""
                INSERT INTO notifications (event_id, order_id, user_id, channel, recipient, message)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, channel) DO NOTHING""",
                event.eventId(), event.orderId(), event.userId(), channel.name(), recipient, message);
        if (inserted == 1) {
            // Sent inside the transaction: if sending throws, the row rolls back and the message is redelivered.
            sender.send(channel, recipient, message);
        }
        return inserted;
    }
}
