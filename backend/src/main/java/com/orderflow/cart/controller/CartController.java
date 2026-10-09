package com.orderflow.cart.controller;

import com.orderflow.auth.security.CurrentUserId;
import com.orderflow.cart.dto.AddCartItemRequest;
import com.orderflow.cart.dto.CartResponse;
import com.orderflow.cart.dto.UpdateCartItemRequest;
import com.orderflow.cart.service.CartService;
import com.orderflow.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartResponse> get(@CurrentUserId Long userId) {
        return ApiResponse.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> add(@CurrentUserId Long userId, @Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.ok(cartService.addItem(userId, request));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<CartResponse> update(@CurrentUserId Long userId, @PathVariable Long itemId,
                                           @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.ok(cartService.updateItem(userId, itemId, request.quantity()));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> remove(@CurrentUserId Long userId, @PathVariable Long itemId) {
        return ApiResponse.ok(cartService.removeItem(userId, itemId));
    }
}
