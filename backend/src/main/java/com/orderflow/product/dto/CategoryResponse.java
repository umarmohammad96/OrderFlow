package com.orderflow.product.dto;

import com.orderflow.product.domain.Category;

public record CategoryResponse(Long id, String name) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName());
    }
}
