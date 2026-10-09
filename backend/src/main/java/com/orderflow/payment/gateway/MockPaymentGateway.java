package com.orderflow.payment.gateway;

import java.math.BigDecimal;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deterministic stand-in for a card processor, driven by test tokens in the style of real providers:
 * <ul>
 *   <li>{@code tok_visa} (default) — approved</li>
 *   <li>{@code tok_declined} — declined</li>
 *   <li>{@code tok_insufficient_funds} — declined</li>
 *   <li>{@code tok_gateway_error} — provider error (exception)</li>
 * </ul>
 * A configurable latency simulates the network round trip that must not happen inside a DB transaction.
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final Duration latency;

    public MockPaymentGateway(@Value("${orderflow.payment.mock-latency}") Duration latency) {
        this.latency = latency;
    }

    @Override
    public GatewayResult authorize(String transactionReference, BigDecimal amount, String paymentToken) {
        sleep();
        String token = paymentToken == null ? "tok_visa" : paymentToken;
        log.info("[MOCK-GATEWAY] authorize ref={} amount={} token={}", transactionReference, amount, token);
        return switch (token) {
            case "tok_declined" -> GatewayResult.decline("Card declined");
            case "tok_insufficient_funds" -> GatewayResult.decline("Insufficient funds");
            case "tok_gateway_error" -> throw new IllegalStateException("Payment provider unavailable");
            default -> GatewayResult.approve();
        };
    }

    private void sleep() {
        if (latency.isZero() || latency.isNegative()) {
            return;
        }
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
