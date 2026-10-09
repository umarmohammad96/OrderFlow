package com.orderflow.order.service;

import com.orderflow.order.dto.OrderResponse;

/** @param replayed true when the response was served from the idempotency store rather than freshly created */
public record OrderCreationResult(OrderResponse order, boolean replayed) {
}
