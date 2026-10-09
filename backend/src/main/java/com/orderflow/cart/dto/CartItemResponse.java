package com.orderflow.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(Long id, Long productId, String productName, BigDecimal unitPrice, int quantity,
                               BigDecimal lineTotal, int availableQuantity, boolean purchasable) {
}
