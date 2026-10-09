package com.orderflow.inventory.dto;

import com.orderflow.inventory.domain.Inventory;

public record InventoryResponse(Long productId, int availableQuantity, int reservedQuantity, long version) {

    public static InventoryResponse from(Inventory i) {
        return new InventoryResponse(i.getProductId(), i.getAvailableQuantity(), i.getReservedQuantity(), i.getVersion());
    }
}
