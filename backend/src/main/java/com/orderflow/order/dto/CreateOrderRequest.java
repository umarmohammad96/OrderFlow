package com.orderflow.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Order lines are explicit so the request is self-describing and can be hashed for idempotency checks. */
public record CreateOrderRequest(@NotEmpty @Size(max = 50) List<@NotNull @Valid OrderItemRequest> items) {
}
