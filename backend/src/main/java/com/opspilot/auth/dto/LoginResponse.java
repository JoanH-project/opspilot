package com.opspilot.auth.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
    public record UserSummary(Long id, String email, String name) { }
}
