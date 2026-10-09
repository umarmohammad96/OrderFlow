package com.orderflow.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orderflow.auth.domain.Role;
import com.orderflow.auth.dto.UserResponse;
import com.orderflow.auth.service.AuthService;
import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.outbox.OutboxService;
import com.orderflow.inventory.service.InventoryService;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.gateway.PaymentGateway;
import com.orderflow.payment.gateway.PaymentGateway.GatewayResult;
import com.orderflow.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    OrderRepository orders;
    @Mock
    PaymentRepository payments;
    @Mock
    PaymentGateway gateway;
    @Mock
    InventoryService inventoryService;
    @Mock
    OutboxService outboxService;
    @Mock
    AuthService authService;
    @Mock
    TransactionTemplate tx;

    PaymentService service;
    Order order;
    Payment pending;

    @BeforeEach
    void setUp() {
        service = new PaymentService(orders, payments, gateway, inventoryService, outboxService, authService, tx);
        // Execute transaction callbacks inline.
        when(tx.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));

        order = new Order(1L);
        order.addItem(10L, "Thing", 2, new BigDecimal("5.00"));
        order.setTotalAmount(new BigDecimal("10.00"));
        pending = new Payment(5L, new BigDecimal("10.00"), "PAY-1");

        when(orders.findByIdForUpdate(5L)).thenReturn(Optional.of(order));
        when(payments.findFirstByOrderIdAndStatus(5L, PaymentStatus.SUCCEEDED)).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payments.findById(any())).thenReturn(Optional.of(pending));
        when(authService.currentUser(1L)).thenReturn(new UserResponse(1L, "Asha", "asha@example.com", Role.CUSTOMER, Instant.now()));
    }

    @Test
    void approvedPaymentConfirmsOrderCommitsStockAndEnqueuesEvent() {
        when(gateway.authorize(anyString(), any(), eq("tok_visa"))).thenReturn(GatewayResult.approve());

        PaymentResult result = service.pay(1L, 5L, "tok_visa");

        assertThat(result.replayed()).isFalse();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(inventoryService).commit(Map.of(10L, 2));
        verify(outboxService).enqueue(eq("Order"), eq(5L), eq("OrderConfirmedEvent"), eq("order.confirmed"), any());
    }

    @Test
    void declinedPaymentLeavesOrderPaymentPendingForRetry() {
        when(gateway.authorize(anyString(), any(), any())).thenReturn(GatewayResult.decline("Card declined"));

        PaymentResult result = service.pay(1L, 5L, "tok_declined");

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.payment().failureReason()).isEqualTo("Card declined");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        verifyNoInteractions(inventoryService, outboxService);
    }

    @Test
    void gatewayExceptionIsRecordedAsFailure() {
        when(gateway.authorize(anyString(), any(), any())).thenThrow(new IllegalStateException("timeout"));

        PaymentResult result = service.pay(1L, 5L, "tok_gateway_error");

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.payment().failureReason()).contains("timeout");
    }

    @Test
    void alreadyPaidOrderReturnsExistingPaymentWithoutCharging() {
        Payment succeeded = new Payment(5L, new BigDecimal("10.00"), "PAY-0");
        succeeded.succeed();
        when(payments.findFirstByOrderIdAndStatus(5L, PaymentStatus.SUCCEEDED)).thenReturn(Optional.of(succeeded));

        PaymentResult result = service.pay(1L, 5L, "tok_visa");

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment().transactionReference()).isEqualTo("PAY-0");
        verifyNoInteractions(gateway);
    }

    @Test
    void concurrentAttemptIsRejectedWhileOneIsPending() {
        when(payments.existsByOrderIdAndStatus(5L, PaymentStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.pay(1L, 5L, "tok_visa"))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("PAYMENT_IN_PROGRESS"));
        verifyNoInteractions(gateway);
    }

    @Test
    void cancelledOrderCannotBePaid() {
        order.transitionTo(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> service.pay(1L, 5L, "tok_visa"))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("ORDER_CANCELLED"));
        verifyNoInteractions(gateway);
    }

    @Test
    void authorisationIsVoidedIfOrderWasCancelledDuringGatewayCall() {
        when(gateway.authorize(anyString(), any(), any())).thenAnswer(inv -> {
            order.transitionTo(OrderStatus.CANCELLED); // a cancel request won the race
            return GatewayResult.approve();
        });

        PaymentResult result = service.pay(1L, 5L, "tok_visa");

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.VOIDED);
        verify(inventoryService, never()).commit(any());
        verifyNoInteractions(outboxService);
    }
}
