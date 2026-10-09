package com.orderflow.payment.service;

import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.repository.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * If the process dies between the gateway call and tx2, a payment stays PENDING and would block retries forever.
 * This job fails such payments after a timeout so the customer can pay again. (With a real provider it would first
 * query the provider for the authoritative status.)
 */
@Component
public class StalePaymentRecovery {

    private static final Logger log = LoggerFactory.getLogger(StalePaymentRecovery.class);
    static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final PaymentRepository payments;

    public StalePaymentRecovery(PaymentRepository payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void failStalePayments() {
        List<Payment> stale = payments.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, Instant.now().minus(TIMEOUT));
        stale.forEach(p -> {
            p.fail("Timed out waiting for payment provider");
            log.warn("Marked stale payment {} for order {} as FAILED", p.getId(), p.getOrderId());
        });
    }
}
