package com.orderflow.payment.controller;

import com.orderflow.auth.security.CurrentUserId;
import com.orderflow.auth.security.SecurityUtils;
import com.orderflow.common.response.ApiError;
import com.orderflow.common.response.ApiResponse;
import com.orderflow.payment.dto.PaymentRequest;
import com.orderflow.payment.dto.PaymentResponse;
import com.orderflow.payment.service.PaymentResult;
import com.orderflow.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 201 — payment succeeded; 200 — order was already paid (retry-safe replay);
     * 402 — declined (order stays PAYMENT_PENDING, retry allowed); 409 — voided because the order was cancelled.
     */
    @PostMapping("/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(@CurrentUserId Long userId, @PathVariable Long orderId,
                                                            @Valid @RequestBody(required = false) PaymentRequest request) {
        PaymentResult result = paymentService.pay(userId, orderId, request == null ? null : request.paymentToken());
        PaymentResponse payment = result.payment();
        if (result.replayed()) {
            return ResponseEntity.ok(ApiResponse.ok(payment));
        }
        return switch (payment.status()) {
            case SUCCEEDED -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(payment));
            case FAILED -> failure(HttpStatus.PAYMENT_REQUIRED, payment, "PAYMENT_DECLINED");
            default -> failure(HttpStatus.CONFLICT, payment, "PAYMENT_VOIDED");
        };
    }

    @GetMapping("/{orderId}")
    public ApiResponse<List<PaymentResponse>> list(@CurrentUserId Long userId, @PathVariable Long orderId) {
        return ApiResponse.ok(paymentService.listForOrder(userId, SecurityUtils.currentUserIsAdmin(), orderId));
    }

    private static ResponseEntity<ApiResponse<PaymentResponse>> failure(HttpStatus status, PaymentResponse payment, String code) {
        return ResponseEntity.status(status).body(ApiResponse.failure(new ApiError(code, payment.failureReason()), payment));
    }
}
