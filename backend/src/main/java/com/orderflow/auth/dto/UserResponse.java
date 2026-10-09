package com.orderflow.auth.dto;

import com.orderflow.auth.domain.Role;
import com.orderflow.auth.domain.User;
import java.time.Instant;

public record UserResponse(Long id, String name, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
