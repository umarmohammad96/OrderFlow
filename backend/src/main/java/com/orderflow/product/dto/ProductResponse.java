package com.orderflow.product.dto;

import com.orderflow.product.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;

/** Catalogue data only — safe to cache. Live stock is merged separately (see {@link ProductView}). */
public record ProductResponse(Long id, String name, String description, BigDecimal price, Long categoryId,
                              String categoryName, boolean active, Instant createdAt, Instant updatedAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getCategory().getId(),
                p.getCategory().getName(), p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
