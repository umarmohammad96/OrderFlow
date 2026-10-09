package com.orderflow.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Cached catalogue data combined with live (never cached) stock. */
public record ProductView(Long id, String name, String description, BigDecimal price, Long categoryId,
                          String categoryName, boolean active, int availableQuantity, boolean inStock,
                          Instant createdAt, Instant updatedAt) {

    public static ProductView of(ProductResponse p, int availableQuantity) {
        return new ProductView(p.id(), p.name(), p.description(), p.price(), p.categoryId(), p.categoryName(),
                p.active(), availableQuantity, availableQuantity > 0, p.createdAt(), p.updatedAt());
    }
}
