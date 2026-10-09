package com.orderflow.payment.dto;

import com.orderflow.order.domain.OrderStatus;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long orderId, BigDecimal amount, PaymentStatus status,
                              String transactionReference, String failureReason, OrderStatus orderStatus,
                              Instant createdAt) {

    public static PaymentResponse from(Payment p, OrderStatus orderStatus) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(), p.getTransactionReference(),
                p.getFailureReason(), orderStatus, p.getCreatedAt());
    }
}
