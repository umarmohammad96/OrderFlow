package com.orderflow.inventory.service;

import com.orderflow.common.exception.ConflictException;

public class OutOfStockException extends ConflictException {

    private final Long productId;

    public OutOfStockException(Long productId) {
        super("OUT_OF_STOCK", "Insufficient stock for product " + productId);
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
