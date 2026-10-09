package com.orderflow.order.dto;

import com.orderflow.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(Long id, Long userId, OrderStatus status, BigDecimal totalAmount, int itemCount,
                                   Instant createdAt, Instant updatedAt) {
}
