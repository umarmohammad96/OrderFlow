package com.orderflow.payment.gateway;

import java.math.BigDecimal;

/** Port to an external payment provider. Implementations must be safe to call outside a DB transaction. */
public interface PaymentGateway {

    GatewayResult authorize(String transactionReference, BigDecimal amount, String paymentToken);

    record GatewayResult(boolean approved, String declineReason) {

        public static GatewayResult approve() {
            return new GatewayResult(true, null);
        }

        public static GatewayResult decline(String reason) {
            return new GatewayResult(false, reason);
        }
    }
}
