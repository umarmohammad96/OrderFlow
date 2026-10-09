package com.orderflow.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(Long cartId, List<CartItemResponse> items, int totalQuantity, BigDecimal subtotal,
                           Instant updatedAt) {

    public static CartResponse empty() {
        return new CartResponse(null, List.of(), 0, BigDecimal.ZERO.setScale(2), null);
    }
}
