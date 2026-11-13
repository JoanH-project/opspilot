package com.opspilot.knowledge;

public class KnowledgeReindexTooLargeException extends RuntimeException {
    public KnowledgeReindexTooLargeException() {
        super("Workspace reindex exceeds the supported document limit");
    }
}
