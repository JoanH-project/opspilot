package com.opspilot.auth.ratelimit;

public class TooManyAuthenticationAttemptsException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooManyAuthenticationAttemptsException(long retryAfterSeconds) {
        super("Too many authentication attempts. Retry later.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
