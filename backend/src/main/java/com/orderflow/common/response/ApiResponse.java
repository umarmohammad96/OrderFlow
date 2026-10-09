package com.orderflow.common.response;

import com.orderflow.common.web.RequestIdFilter;
import java.time.Instant;
import org.slf4j.MDC;

/**
 * Uniform response envelope: every endpoint returns either {@code data} or {@code error},
 * plus the request ID so clients can quote it when reporting problems.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, String requestId, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, MDC.get(RequestIdFilter.MDC_KEY), Instant.now());
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return failure(error, null);
    }

    /** A failed outcome that still carries a resource, e.g. a declined payment record. */
    public static <T> ApiResponse<T> failure(ApiError error, T data) {
        return new ApiResponse<>(false, data, error, MDC.get(RequestIdFilter.MDC_KEY), Instant.now());
    }
}
