package com.orderflow.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published (via the outbox) when payment succeeds and the order becomes CONFIRMED. */
public record OrderConfirmedEvent(UUID eventId, Long orderId, Long userId, String customerName, String customerEmail,
                                  BigDecimal totalAmount, int itemCount, String paymentReference, Instant confirmedAt) {

    public static final String TYPE = "OrderConfirmedEvent";
}
