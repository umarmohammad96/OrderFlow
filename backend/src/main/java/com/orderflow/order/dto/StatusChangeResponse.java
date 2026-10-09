package com.orderflow.order.dto;

import com.orderflow.order.domain.OrderStatus;
import java.time.Instant;

public record StatusChangeResponse(OrderStatus from, OrderStatus to, Instant changedAt) {
}
