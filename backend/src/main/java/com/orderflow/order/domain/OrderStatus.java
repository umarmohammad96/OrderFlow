package com.orderflow.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle (blueprint §7):
 * <pre>
 * CREATED -> PAYMENT_PENDING -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
 *    |              |
 *    +--------------+--> CANCELLED
 * </pre>
 * Only the transitions listed here are legal; everything else is rejected.
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case CREATED -> EnumSet.of(PAYMENT_PENDING, CANCELLED);
            case PAYMENT_PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PROCESSING);
            case PROCESSING -> EnumSet.of(SHIPPED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedNext().contains(target);
    }

    /** Stock for orders in these states is reserved but not yet sold. */
    public boolean holdsReservation() {
        return this == CREATED || this == PAYMENT_PENDING;
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
