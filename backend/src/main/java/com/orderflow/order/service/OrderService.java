package com.orderflow.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.cart.service.CartService;
import com.orderflow.common.exception.BusinessRuleException;
import com.orderflow.common.exception.NotFoundException;
import com.orderflow.common.idempotency.IdempotencyService;
import com.orderflow.common.idempotency.RequestHasher;
import com.orderflow.common.response.PageResponse;
import com.orderflow.inventory.service.InventoryService;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderItemRequest;
import com.orderflow.order.dto.OrderMapper;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.dto.OrderSummaryResponse;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.product.dto.ProductResponse;
import com.orderflow.product.service.ProductService;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final Set<OrderStatus> ADMIN_SETTABLE = EnumSet.of(
            OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final OrderRepository orders;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final CartService cartService;
    private final PricingService pricingService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orders, ProductService productService, InventoryService inventoryService,
                        CartService cartService, PricingService pricingService, IdempotencyService idempotencyService,
                        ObjectMapper objectMapper) {
        this.orders = orders;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.cartService = cartService;
        this.pricingService = pricingService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * One transaction covers: idempotency-key claim, stock reservation, order + items insert, cart cleanup and the
     * stored idempotent response. Any failure rolls back all of it.
     */
    @Transactional
    public OrderCreationResult createOrder(Long userId, String idempotencyKey, CreateOrderRequest request) {
        Map<Long, Integer> quantities = normalize(request.items());
        String requestHash = RequestHasher.sha256(canonical(quantities));

        Optional<String> stored = idempotencyService.begin(userId, idempotencyKey, requestHash);
        if (stored.isPresent()) {
            log.info("Replaying order response for Idempotency-Key {}", idempotencyKey);
            return new OrderCreationResult(fromJson(stored.get()), true);
        }

        Map<Long, ProductResponse> products = productService.getProducts(quantities.keySet());
        quantities.keySet().forEach(productId -> {
            ProductResponse product = products.get(productId);
            if (product == null || !product.active()) {
                throw new BusinessRuleException("PRODUCT_UNAVAILABLE", "Product " + productId + " is not available");
            }
        });

        inventoryService.reserve(quantities);

        List<PricingService.PricedLine> lines = pricingService.price(quantities.entrySet().stream()
                .map(e -> {
                    ProductResponse p = products.get(e.getKey());
                    return new PricingService.Line(p.id(), p.name(), e.getValue(), p.price());
                }).toList());
        Order order = new Order(userId);
        lines.forEach(l -> order.addItem(l.productId(), l.productName(), l.quantity(), l.unitPrice()));
        order.setTotalAmount(pricingService.total(lines));
        orders.saveAndFlush(order);

        cartService.removeProducts(userId, quantities.keySet());

        OrderResponse response = OrderMapper.toResponse(order);
        idempotencyService.complete(userId, idempotencyKey, toJson(response));
        log.info("Created order {} for user {} total={}", order.getId(), userId, order.getTotalAmount());
        return new OrderCreationResult(response, false);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long userId, boolean admin, Long orderId) {
        return OrderMapper.toResponse(findVisible(orderId, userId, admin, false));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listForUser(Long userId, int page, int size) {
        return PageResponse.from(orders.findByUserId(userId, PageRequest.of(page, size, NEWEST_FIRST)), OrderMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> listAll(OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        return PageResponse.from(status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable),
                OrderMapper::toSummary);
    }

    /** Cancels and, if stock is still reserved, returns it to the available pool — atomically. */
    @Transactional
    public OrderResponse cancel(Long userId, boolean admin, Long orderId) {
        Order order = findVisible(orderId, userId, admin, true);
        boolean releaseStock = order.getStatus().holdsReservation();
        order.transitionTo(OrderStatus.CANCELLED);
        if (releaseStock) {
            inventoryService.release(order.quantitiesByProduct());
        }
        log.info("Order {} cancelled (stock released={})", orderId, releaseStock);
        return OrderMapper.toResponse(orders.saveAndFlush(order));
    }

    /** Fulfilment transitions driven by operations staff; payment-driven states cannot be set manually. */
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus target) {
        if (!ADMIN_SETTABLE.contains(target)) {
            throw new BusinessRuleException("STATUS_NOT_MANUALLY_SETTABLE",
                    "Status " + target + " cannot be set manually; allowed: " + ADMIN_SETTABLE);
        }
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        order.transitionTo(target);
        return OrderMapper.toResponse(orders.saveAndFlush(order));
    }

    private Order findVisible(Long orderId, Long userId, boolean admin, boolean forUpdate) {
        Optional<Order> order = forUpdate ? orders.findByIdForUpdate(orderId) : orders.findById(orderId);
        // Other users' orders are reported as "not found" so IDs cannot be probed.
        return order.filter(o -> admin || o.isOwnedBy(userId)).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    /** Merges duplicate lines for the same product and orders them by product ID. */
    static Map<Long, Integer> normalize(List<OrderItemRequest> items) {
        Map<Long, Integer> merged = items.stream()
                .collect(Collectors.toMap(OrderItemRequest::productId, OrderItemRequest::quantity, Integer::sum, TreeMap::new));
        merged.forEach((productId, qty) -> {
            if (qty > 99) {
                throw new BusinessRuleException("ORDER_QUANTITY_LIMIT", "At most 99 units of product " + productId);
            }
        });
        return merged;
    }

    static String canonical(Map<Long, Integer> quantities) {
        return quantities.entrySet().stream().map(e -> e.getKey() + "x" + e.getValue()).collect(Collectors.joining(","));
    }

    private String toJson(OrderResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise order response", e);
        }
    }

    private OrderResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, OrderResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialise stored order response", e);
        }
    }
}
