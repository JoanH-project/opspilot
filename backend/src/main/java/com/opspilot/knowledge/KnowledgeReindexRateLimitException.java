package com.opspilot.knowledge;

public class KnowledgeReindexRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public KnowledgeReindexRateLimitException(long retryAfterSeconds) {
        super("Workspace reindex has recently been requested");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
