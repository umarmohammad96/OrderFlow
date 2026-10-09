package com.orderflow.cart.service;

import com.orderflow.cart.domain.Cart;
import com.orderflow.cart.domain.CartItem;
import com.orderflow.cart.dto.AddCartItemRequest;
import com.orderflow.cart.dto.CartItemResponse;
import com.orderflow.cart.dto.CartLimits;
import com.orderflow.cart.dto.CartResponse;
import com.orderflow.cart.repository.CartRepository;
import com.orderflow.common.exception.BusinessRuleException;
import com.orderflow.common.exception.NotFoundException;
import com.orderflow.inventory.service.InventoryService;
import com.orderflow.product.dto.ProductResponse;
import com.orderflow.product.service.ProductService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository carts;
    private final ProductService productService;
    private final InventoryService inventoryService;

    public CartService(CartRepository carts, ProductService productService, InventoryService inventoryService) {
        this.carts = carts;
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return carts.findByUserId(userId).map(this::toResponse).orElseGet(CartResponse::empty);
    }

    @Transactional
    public CartResponse addItem(Long userId, AddCartItemRequest request) {
        ProductResponse product = productService.getProducts(List.of(request.productId())).get(request.productId());
        if (product == null || !product.active()) {
            throw new NotFoundException("Product", request.productId());
        }
        Cart cart = getOrCreate(userId);
        cart.findItemByProduct(request.productId()).ifPresentOrElse(
                item -> item.setQuantity(checkLimit(item.getQuantity() + request.quantity())),
                () -> cart.addItem(request.productId(), request.quantity()));
        cart.touch();
        return toResponse(carts.saveAndFlush(cart));
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, int quantity) {
        Cart cart = carts.findByUserId(userId).orElseThrow(() -> new NotFoundException("Cart item", itemId));
        CartItem item = cart.findItem(itemId).orElseThrow(() -> new NotFoundException("Cart item", itemId));
        item.setQuantity(checkLimit(quantity));
        cart.touch();
        return toResponse(carts.saveAndFlush(cart));
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = carts.findByUserId(userId).orElseThrow(() -> new NotFoundException("Cart item", itemId));
        CartItem item = cart.findItem(itemId).orElseThrow(() -> new NotFoundException("Cart item", itemId));
        cart.removeItem(item);
        return toResponse(carts.saveAndFlush(cart));
    }

    /** Called from order creation: purchased products leave the cart in the same transaction as the order. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void removeProducts(Long userId, Collection<Long> productIds) {
        carts.removeProducts(userId, productIds);
    }

    private Cart getOrCreate(Long userId) {
        return carts.findByUserId(userId).orElseGet(() -> {
            carts.createIfAbsent(userId);
            return carts.findByUserId(userId).orElseThrow();
        });
    }

    private static int checkLimit(int quantity) {
        if (quantity > CartLimits.MAX_QUANTITY_PER_ITEM) {
            throw new BusinessRuleException("CART_QUANTITY_LIMIT",
                    "At most " + CartLimits.MAX_QUANTITY_PER_ITEM + " units of a product can be in the cart");
        }
        return quantity;
    }

    private CartResponse toResponse(Cart cart) {
        List<Long> productIds = cart.getItems().stream().map(CartItem::getProductId).toList();
        Map<Long, ProductResponse> products = productIds.isEmpty() ? Map.of() : productService.getProducts(productIds);
        Map<Long, Integer> stock = inventoryService.availableQuantities(productIds);

        List<CartItemResponse> items = cart.getItems().stream().map(item -> {
            ProductResponse p = products.get(item.getProductId());
            BigDecimal lineTotal = p.price().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_EVEN);
            int available = stock.getOrDefault(item.getProductId(), 0);
            return new CartItemResponse(item.getId(), p.id(), p.name(), p.price(), item.getQuantity(), lineTotal,
                    available, p.active() && available >= item.getQuantity());
        }).toList();

        BigDecimal subtotal = items.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
        int totalQuantity = items.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(cart.getId(), items, totalQuantity, subtotal, cart.getUpdatedAt());
    }
}
