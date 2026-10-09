package com.orderflow.product.controller;

import com.orderflow.auth.security.SecurityUtils;
import com.orderflow.common.exception.NotFoundException;
import com.orderflow.common.response.ApiResponse;
import com.orderflow.common.response.PageResponse;
import com.orderflow.inventory.service.InventoryService;
import com.orderflow.product.dto.CreateProductRequest;
import com.orderflow.product.dto.ProductQuery;
import com.orderflow.product.dto.ProductResponse;
import com.orderflow.product.dto.ProductSort;
import com.orderflow.product.dto.ProductView;
import com.orderflow.product.dto.UpdateProductRequest;
import com.orderflow.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;

    public ProductController(ProductService productService, InventoryService inventoryService) {
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductView>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "NEWEST") ProductSort sort) {
        PageResponse<ProductResponse> products = productService.listActive(new ProductQuery(categoryId, q, sort, page, size));
        Map<Long, Integer> stock = inventoryService.availableQuantities(
                products.content().stream().map(ProductResponse::id).toList());
        return ApiResponse.ok(products.map(p -> ProductView.of(p, stock.getOrDefault(p.id(), 0))));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductView> get(@PathVariable Long id) {
        ProductResponse product = productService.getProduct(id);
        if (!product.active() && !SecurityUtils.currentUserIsAdmin()) {
            throw new NotFoundException("Product", id);
        }
        int available = inventoryService.availableQuantities(List.of(id)).getOrDefault(id, 0);
        return ApiResponse.ok(ProductView.of(product, available));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        productService.deactivate(id);
    }
}
