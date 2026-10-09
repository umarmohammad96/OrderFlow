package com.orderflow.notification.dto;

import com.orderflow.notification.domain.Notification;
import com.orderflow.notification.domain.NotificationChannel;
import java.time.Instant;

public record NotificationResponse(Long id, Long orderId, NotificationChannel channel, String recipient, String message,
                                   Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getOrderId(), n.getChannel(), n.getRecipient(), n.getMessage(),
                n.getCreatedAt());
    }
}
