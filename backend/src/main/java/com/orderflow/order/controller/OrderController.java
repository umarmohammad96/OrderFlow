package com.orderflow.order.controller;

import com.orderflow.auth.security.CurrentUserId;
import com.orderflow.auth.security.SecurityUtils;
import com.orderflow.common.response.ApiResponse;
import com.orderflow.common.response.PageResponse;
import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.dto.OrderSummaryResponse;
import com.orderflow.order.service.OrderCreationResult;
import com.orderflow.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(
            @CurrentUserId Long userId,
            @RequestHeader(IDEMPOTENCY_HEADER)
            @Pattern(regexp = "[A-Za-z0-9._:-]{8,100}", message = "must be 8-100 characters of [A-Za-z0-9._:-]")
            String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderCreationResult result = orderService.createOrder(userId, idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/orders/" + result.order().id()))
                .header(REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(ApiResponse.ok(result.order()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.ok(orderService.getOrder(userId, SecurityUtils.currentUserIsAdmin(), id));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(@CurrentUserId Long userId,
                                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                                @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(orderService.listForUser(userId, page, size));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@CurrentUserId Long userId, @PathVariable Long id) {
        return ApiResponse.ok(orderService.cancel(userId, SecurityUtils.currentUserIsAdmin(), id));
    }
}
