package com.opspilot.knowledge;

public class KnowledgeAiUnavailableException extends RuntimeException {
    public KnowledgeAiUnavailableException() {
        super("Knowledge service is temporarily unavailable");
    }
}
