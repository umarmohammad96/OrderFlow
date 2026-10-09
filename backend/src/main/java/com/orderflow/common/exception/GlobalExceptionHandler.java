package com.orderflow.common.exception;

import com.orderflow.common.response.ApiError;
import com.orderflow.common.response.ApiError.FieldViolation;
import com.orderflow.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Request failed: {}", ex.getMessage(), ex);
        } else {
            log.info("Request rejected: {} {}", ex.getCode(), ex.getMessage());
        }
        return respond(ex.getStatus(), new ApiError(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldViolation(e.getField(), e.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, new ApiError("VALIDATION_FAILED", "Request validation failed", violations));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldViolation(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, new ApiError("VALIDATION_FAILED", "Request validation failed", violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, new ApiError("VALIDATION_FAILED", "Request validation failed", violations));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        String code = "Idempotency-Key".equalsIgnoreCase(ex.getHeaderName()) ? "IDEMPOTENCY_KEY_REQUIRED" : "MISSING_HEADER";
        return respond(HttpStatus.BAD_REQUEST, new ApiError(code, "Required header '" + ex.getHeaderName() + "' is missing"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String allowed = ex.getRequiredType() != null && ex.getRequiredType().isEnum()
                ? "; allowed: " + java.util.Arrays.toString(ex.getRequiredType().getEnumConstants()) : "";
        return respond(HttpStatus.BAD_REQUEST, new ApiError("MALFORMED_REQUEST",
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'" + allowed));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, new ApiError("MALFORMED_REQUEST", "Malformed request: " + rootMessage(ex)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, new ApiError("FORBIDDEN", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return respond(HttpStatus.CONFLICT, new ApiError("CONCURRENT_MODIFICATION",
                "The resource was modified concurrently; reload and retry"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", rootMessage(ex));
        return respond(HttpStatus.CONFLICT, new ApiError("DATA_CONFLICT", "The request conflicts with existing data"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, new ApiError("METHOD_NOT_ALLOWED", ex.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, new ApiError("UNSUPPORTED_MEDIA_TYPE", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, new ApiError("NOT_FOUND", "No endpoint " + ex.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, new ApiError("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    private static ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, ApiError error) {
        return ResponseEntity.status(status).body(ApiResponse.failure(error));
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
    }
}
