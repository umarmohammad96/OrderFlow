package com.orderflow.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.common.response.ApiError;
import com.orderflow.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes 401/403 responses produced by the security filter chain in the standard API envelope. */
@Component
public class JsonSecurityErrorHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED,
                new ApiError("UNAUTHORIZED", "Authentication is required or the token is invalid"));
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, HttpStatus.FORBIDDEN,
                new ApiError("FORBIDDEN", "You do not have permission to perform this action"));
    }

    private void write(HttpServletResponse response, HttpStatus status, ApiError error) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(error));
    }
}
