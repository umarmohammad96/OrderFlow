package com.orderflow.product.dto;

import org.springframework.data.domain.Sort;

/** Whitelisted sort orders: arbitrary sort properties would allow probing columns and explode the cache key space. */
public enum ProductSort {
    NEWEST(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))),
    PRICE_ASC(Sort.by(Sort.Direction.ASC, "price").and(Sort.by("id"))),
    PRICE_DESC(Sort.by(Sort.Direction.DESC, "price").and(Sort.by("id"))),
    NAME(Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id")));

    private final Sort sort;

    ProductSort(Sort sort) {
        this.sort = sort;
    }

    public Sort sort() {
        return sort;
    }
}
