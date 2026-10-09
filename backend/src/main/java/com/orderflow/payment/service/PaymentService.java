package com.orderflow.payment.service;

import com.orderflow.auth.dto.UserResponse;
import com.orderflow.auth.service.AuthService;
import com.orderflow.common.exception.ConflictException;
import com.orderflow.common.exception.NotFoundException;
import com.orderflow.common.messaging.MessagingTopology;
import com.orderflow.common.outbox.OutboxService;
import com.orderflow.inventory.service.InventoryService;
import com.orderflow.order.domain.Order;
import com.orderflow.order.domain.OrderItem;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.repository.OrderRepository;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.dto.OrderConfirmedEvent;
import com.orderflow.payment.dto.PaymentResponse;
import com.orderflow.payment.gateway.PaymentGateway;
import com.orderflow.payment.gateway.PaymentGateway.GatewayResult;
import com.orderflow.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Payment flow, split so that the slow external gateway call never holds a DB transaction or row lock:
 * <ol>
 *   <li><b>tx1</b> — lock order, validate, CREATED→PAYMENT_PENDING, insert PENDING payment.</li>
 *   <li><b>no tx</b> — call the payment gateway.</li>
 *   <li><b>tx2</b> — lock order, record result; on success PAYMENT_PENDING→CONFIRMED, convert the stock
 *       reservation into a sale and write OrderConfirmedEvent to the outbox — all atomically.</li>
 * </ol>
 * Retry safety: an order that already has a SUCCEEDED payment returns it instead of charging again; a concurrent
 * attempt while one is PENDING gets 409 (also enforced by a partial unique index).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final InventoryService inventoryService;
    private final OutboxService outboxService;
    private final AuthService authService;
    private final TransactionTemplate tx;

    public PaymentService(OrderRepository orders, PaymentRepository payments, PaymentGateway gateway,
                          InventoryService inventoryService, OutboxService outboxService, AuthService authService,
                          TransactionTemplate tx) {
        this.orders = orders;
        this.payments = payments;
        this.gateway = gateway;
        this.inventoryService = inventoryService;
        this.outboxService = outboxService;
        this.authService = authService;
        this.tx = tx;
    }

    public PaymentResult pay(Long userId, Long orderId, String paymentToken) {
        Attempt attempt = tx.execute(status -> begin(userId, orderId));
        if (attempt.existing() != null) {
            return new PaymentResult(attempt.existing(), true);
        }

        GatewayResult result;
        try {
            result = gateway.authorize(attempt.reference(), attempt.amount(), paymentToken);
        } catch (RuntimeException e) {
            log.warn("Gateway error for payment {}: {}", attempt.paymentId(), e.getMessage());
            result = GatewayResult.decline("Payment provider error: " + e.getMessage());
        }

        GatewayResult finalResult = result;
        return new PaymentResult(tx.execute(status -> complete(attempt.paymentId(), orderId, finalResult)), false);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForOrder(Long userId, boolean admin, Long orderId) {
        Order order = orders.findById(orderId).filter(o -> admin || o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        return payments.findByOrderIdOrderByIdDesc(orderId).stream()
                .map(p -> PaymentResponse.from(p, order.getStatus())).toList();
    }

    private Attempt begin(Long userId, Long orderId) {
        Order order = orders.findByIdForUpdate(orderId).filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId));

        var succeeded = payments.findFirstByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED);
        if (succeeded.isPresent()) {
            return Attempt.replay(PaymentResponse.from(succeeded.get(), order.getStatus()));
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("ORDER_CANCELLED", "Order " + orderId + " is cancelled");
        }
        if (!order.getStatus().holdsReservation()) {
            throw new ConflictException("ORDER_NOT_PAYABLE", "Order " + orderId + " is " + order.getStatus());
        }
        if (payments.existsByOrderIdAndStatus(orderId, PaymentStatus.PENDING)) {
            throw new ConflictException("PAYMENT_IN_PROGRESS", "A payment for order " + orderId + " is already in progress");
        }
        if (order.getStatus() == OrderStatus.CREATED) {
            order.transitionTo(OrderStatus.PAYMENT_PENDING);
        }
        Payment payment = payments.saveAndFlush(new Payment(orderId, order.getTotalAmount(), "PAY-" + UUID.randomUUID()));
        return new Attempt(payment.getId(), payment.getTransactionReference(), payment.getAmount(), null);
    }

    private PaymentResponse complete(Long paymentId, Long orderId, GatewayResult result) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        Payment payment = payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment", paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            // Already resolved elsewhere (e.g. timed out by StalePaymentRecovery); never overwrite a final state.
            log.warn("Payment {} already {}; ignoring late gateway result", paymentId, payment.getStatus());
            return PaymentResponse.from(payment, order.getStatus());
        }
        if (!result.approved()) {
            payment.fail(result.declineReason());
            log.info("Payment {} for order {} declined: {}", paymentId, orderId, result.declineReason());
        } else if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            // Order was cancelled while the gateway call was in flight: do not capture.
            payment.voidAuthorisation("Order was " + order.getStatus() + " before capture");
            log.info("Payment {} voided: order {} is {}", paymentId, orderId, order.getStatus());
        } else {
            payment.succeed();
            order.transitionTo(OrderStatus.CONFIRMED);
            inventoryService.commit(order.quantitiesByProduct());
            UserResponse customer = authService.currentUser(order.getUserId());
            int itemCount = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
            outboxService.enqueue("Order", orderId, OrderConfirmedEvent.TYPE, MessagingTopology.ORDER_CONFIRMED_ROUTING_KEY,
                    eventId -> new OrderConfirmedEvent(eventId, orderId, customer.id(), customer.name(), customer.email(),
                            order.getTotalAmount(), itemCount, payment.getTransactionReference(), Instant.now()));
            log.info("Payment {} succeeded; order {} confirmed", paymentId, orderId);
        }
        payments.saveAndFlush(payment);
        orders.saveAndFlush(order);
        return PaymentResponse.from(payment, order.getStatus());
    }

    private record Attempt(Long paymentId, String reference, BigDecimal amount, PaymentResponse existing) {

        static Attempt replay(PaymentResponse existing) {
            return new Attempt(null, null, null, existing);
        }
    }
}
