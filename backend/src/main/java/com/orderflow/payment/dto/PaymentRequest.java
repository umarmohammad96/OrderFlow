package com.orderflow.payment.dto;

import jakarta.validation.constraints.Pattern;

/** {@code paymentToken} selects the mock gateway outcome (see MockPaymentGateway); defaults to an approving token. */
public record PaymentRequest(@Pattern(regexp = "tok_[a-z_]{1,40}") String paymentToken) {
}
