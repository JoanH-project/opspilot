package com.opspilot.auth.ratelimit;

public class AuthenticationProtectionUnavailableException extends RuntimeException {
    public AuthenticationProtectionUnavailableException() {
        super("Authentication protection is temporarily unavailable");
    }
}
