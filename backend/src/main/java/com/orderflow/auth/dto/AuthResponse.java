package com.orderflow.auth.dto;

import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {

    public AuthResponse(String accessToken, Instant expiresAt, UserResponse user) {
        this(accessToken, "Bearer", expiresAt, user);
    }
}
