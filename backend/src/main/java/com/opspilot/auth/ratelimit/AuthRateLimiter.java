package com.opspilot.auth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthRateLimiter {
    void check(AuthAction action, String email, HttpServletRequest request);
}
