package com.orderflow.inventory.controller;

import com.orderflow.common.response.ApiResponse;
import com.orderflow.inventory.dto.AdjustInventoryRequest;
import com.orderflow.inventory.dto.InventoryResponse;
import com.orderflow.inventory.dto.RestockRequest;
import com.orderflow.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only stock management (secured in SecurityConfig). */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> get(@PathVariable Long productId) {
        return ApiResponse.ok(inventoryService.get(productId));
    }

    @PostMapping("/{productId}/restock")
    public ApiResponse<InventoryResponse> restock(@PathVariable Long productId, @Valid @RequestBody RestockRequest request) {
        return ApiResponse.ok(inventoryService.restock(productId, request.quantity()));
    }

    @PutMapping("/{productId}")
    public ApiResponse<InventoryResponse> adjust(@PathVariable Long productId,
                                                 @Valid @RequestBody AdjustInventoryRequest request) {
        return ApiResponse.ok(inventoryService.adjust(productId, request));
    }
}
