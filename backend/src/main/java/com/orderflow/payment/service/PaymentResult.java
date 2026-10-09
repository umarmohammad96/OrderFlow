package com.orderflow.payment.service;

import com.orderflow.payment.dto.PaymentResponse;

/** @param replayed true when the order was already paid and the existing successful payment is returned */
public record PaymentResult(PaymentResponse payment, boolean replayed) {
}
