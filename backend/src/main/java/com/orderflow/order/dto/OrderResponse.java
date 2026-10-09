package com.orderflow.order.dto;

import com.orderflow.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, Long userId, OrderStatus status, BigDecimal totalAmount,
                            List<OrderItemResponse> items, List<StatusChangeResponse> statusHistory,
                            boolean cancellable, boolean payable, Instant createdAt, Instant updatedAt) {
}
