package com.orderflow.payment.domain;

public enum PaymentStatus {
    /** Gateway call in flight. */
    PENDING,
    SUCCEEDED,
    FAILED,
    /** Authorised, but the order was cancelled before capture; the authorisation is released. */
    VOIDED
}
