package com.orderflow.order.controller;

import com.orderflow.common.response.ApiResponse;
import com.orderflow.common.response.PageResponse;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.dto.OrderSummaryResponse;
import com.orderflow.order.dto.UpdateOrderStatusRequest;
import com.orderflow.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Back-office order management; /api/admin/** is restricted to ROLE_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(@RequestParam(required = false) OrderStatus status,
                                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(orderService.listAll(status, page, size));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.ok(orderService.updateStatus(id, request.status()));
    }
}
