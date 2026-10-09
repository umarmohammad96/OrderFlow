package com.orderflow.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Absolute stock correction guarded by optimistic locking: the caller must send the version it last read. */
public record AdjustInventoryRequest(@NotNull @Min(0) @Max(1_000_000) Integer availableQuantity,
                                     @NotNull Long expectedVersion) {
}
