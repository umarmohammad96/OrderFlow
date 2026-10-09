package com.orderflow.common.response;

import java.util.List;

public record ApiError(String code, String message, List<FieldViolation> details) {

    public ApiError(String code, String message) {
        this(code, message, null);
    }

    public record FieldViolation(String field, String message) {
    }
}
