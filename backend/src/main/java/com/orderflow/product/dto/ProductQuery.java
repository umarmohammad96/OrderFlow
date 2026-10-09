package com.orderflow.product.dto;

import java.util.Locale;

public record ProductQuery(Long categoryId, String q, ProductSort sort, int page, int size) {

    public ProductQuery {
        q = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
        sort = sort == null ? ProductSort.NEWEST : sort;
    }

    /** Stable key for the product-list cache. */
    public String cacheKey() {
        return "c=" + (categoryId == null ? "" : categoryId) + "|q=" + (q == null ? "" : q)
                + "|s=" + sort + "|p=" + page + "|n=" + size;
    }
}
