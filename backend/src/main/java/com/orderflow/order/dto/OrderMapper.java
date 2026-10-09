package com.orderflow.order.dto;

import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderItem;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.service.PricingService;

public final class OrderMapper {

    private OrderMapper() {
    }

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getStatus(), order.getTotalAmount(),
                order.getItems().stream().map(OrderMapper::toItem).toList(),
                order.getHistory().stream()
                        .map(h -> new StatusChangeResponse(h.getFromStatus(), h.getToStatus(), h.getChangedAt()))
                        .toList(),
                order.getStatus().canTransitionTo(OrderStatus.CANCELLED),
                order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.PAYMENT_PENDING,
                order.getCreatedAt(), order.getUpdatedAt());
    }

    public static OrderSummaryResponse toSummary(Order order) {
        int itemCount = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return new OrderSummaryResponse(order.getId(), order.getUserId(), order.getStatus(), order.getTotalAmount(),
                itemCount, order.getCreatedAt(), order.getUpdatedAt());
    }

    private static OrderItemResponse toItem(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getProductName(), item.getQuantity(),
                item.getUnitPrice(), PricingService.lineTotal(item.getUnitPrice(), item.getQuantity()));
    }
}
