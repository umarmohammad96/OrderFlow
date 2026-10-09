package com.orderflow.order.domain;

import com.orderflow.common.exception.ConflictException;

public class InvalidStateTransitionException extends ConflictException {

    public InvalidStateTransitionException(OrderStatus from, OrderStatus to) {
        super("INVALID_STATE_TRANSITION", "Order in status " + from + " cannot transition to " + to);
    }
}
