package com.opspilot.user.dto;

import java.time.Instant;
import com.opspilot.user.User;

public record UserResponse(Long id, String email, String name, Instant createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
    }
}
