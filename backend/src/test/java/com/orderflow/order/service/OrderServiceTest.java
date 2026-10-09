package com.orderflow.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderflow.cart.service.CartService;
import com.orderflow.common.exception.BusinessRuleException;
import com.orderflow.common.exception.NotFoundException;
import com.orderflow.common.idempotency.IdempotencyService;
import com.orderflow.inventory.service.InventoryService;
import com.orderflow.inventory.service.OutOfStockException;
import com.orderflow.order.domain.InvalidStateTransitionException;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderItemRequest;
import com.orderflow.order.dto.OrderMapper;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.product.dto.ProductResponse;
import com.orderflow.product.service.ProductService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orders;
    @Mock
    ProductService productService;
    @Mock
    InventoryService inventoryService;
    @Mock
    CartService cartService;
    @Mock
    IdempotencyService idempotencyService;

    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, productService, inventoryService, cartService, new PricingService(),
                idempotencyService, objectMapper);
    }

    private static ProductResponse product(long id, String price, boolean active) {
        return new ProductResponse(id, "P" + id, null, new BigDecimal(price), 1L, "Cat", active, Instant.now(), Instant.now());
    }

    private static CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }

    @Test
    void createsOrderReservingStockAndStoringIdempotentResponse() {
        when(idempotencyService.begin(eq(1L), eq("key-1"), anyString())).thenReturn(Optional.empty());
        when(productService.getProducts(any())).thenReturn(Map.of(10L, product(10, "5.00", true), 20L, product(20, "2.50", true)));

        OrderCreationResult result = service.createOrder(1L, "key-1",
                request(new OrderItemRequest(20L, 2), new OrderItemRequest(10L, 1)));

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().status()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.order().totalAmount()).isEqualByComparingTo("10.00");
        verify(inventoryService).reserve(Map.of(10L, 1, 20L, 2));
        verify(orders).saveAndFlush(any(Order.class));
        verify(cartService).removeProducts(eq(1L), any());
        verify(idempotencyService).complete(eq(1L), eq("key-1"), anyString());
    }

    @Test
    void replaysStoredResponseWithoutTouchingStock() throws Exception {
        Order existing = new Order(1L);
        String stored = objectMapper.writeValueAsString(OrderMapper.toResponse(existing));
        when(idempotencyService.begin(eq(1L), eq("key-1"), anyString())).thenReturn(Optional.of(stored));

        OrderCreationResult result = service.createOrder(1L, "key-1", request(new OrderItemRequest(10L, 1)));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().status()).isEqualTo(OrderStatus.CREATED);
        verifyNoInteractions(inventoryService, orders, cartService, productService);
    }

    @Test
    void outOfStockAbortsBeforeOrderIsSaved() {
        when(idempotencyService.begin(any(), any(), any())).thenReturn(Optional.empty());
        when(productService.getProducts(any())).thenReturn(Map.of(10L, product(10, "5.00", true)));
        doThrow(new OutOfStockException(10L)).when(inventoryService).reserve(any());

        assertThatThrownBy(() -> service.createOrder(1L, "key-1", request(new OrderItemRequest(10L, 1))))
                .isInstanceOf(OutOfStockException.class);
        verify(orders, never()).saveAndFlush(any());
        verify(idempotencyService, never()).complete(any(), any(), any());
    }

    @Test
    void inactiveProductIsRejected() {
        when(idempotencyService.begin(any(), any(), any())).thenReturn(Optional.empty());
        when(productService.getProducts(any())).thenReturn(Map.of(10L, product(10, "5.00", false)));

        assertThatThrownBy(() -> service.createOrder(1L, "key-1", request(new OrderItemRequest(10L, 1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not available");
        verifyNoInteractions(inventoryService);
    }

    @Test
    void normalizeMergesDuplicateLinesAndSortsByProduct() {
        Map<Long, Integer> merged = OrderService.normalize(List.of(
                new OrderItemRequest(5L, 1), new OrderItemRequest(2L, 3), new OrderItemRequest(5L, 4)));

        assertThat(merged).containsExactly(Map.entry(2L, 3), Map.entry(5L, 5));
    }

    @Test
    void canonicalFormIsIndependentOfLineOrder() {
        String a = OrderService.canonical(OrderService.normalize(List.of(new OrderItemRequest(1L, 1), new OrderItemRequest(2L, 2))));
        String b = OrderService.canonical(OrderService.normalize(List.of(new OrderItemRequest(2L, 2), new OrderItemRequest(1L, 1))));
        assertThat(a).isEqualTo(b).isEqualTo("1x1,2x2");
    }

    @Test
    void mergedQuantityAboveLimitIsRejected() {
        assertThatThrownBy(() -> OrderService.normalize(List.of(new OrderItemRequest(1L, 60), new OrderItemRequest(1L, 60))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancellingCreatedOrderReleasesReservedStock() {
        Order order = new Order(1L);
        order.addItem(10L, "P10", 2, new BigDecimal("5.00"));
        when(orders.findByIdForUpdate(99L)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);

        OrderResponse response = service.cancel(1L, false, 99L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).release(Map.of(10L, 2));
    }

    @Test
    void cancellingConfirmedOrderIsRejectedAndStockUntouched() {
        Order order = new Order(1L);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);
        order.transitionTo(OrderStatus.CONFIRMED);
        when(orders.findByIdForUpdate(99L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(1L, false, 99L)).isInstanceOf(InvalidStateTransitionException.class);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void otherUsersOrderIsReportedAsNotFound() {
        when(orders.findByIdForUpdate(99L)).thenReturn(Optional.of(new Order(1L)));

        assertThatThrownBy(() -> service.cancel(2L, false, 99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void adminCannotSetPaymentDrivenStatus() {
        assertThatThrownBy(() -> service.updateStatus(1L, OrderStatus.CONFIRMED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be set manually");
        verifyNoInteractions(orders);
    }
}
