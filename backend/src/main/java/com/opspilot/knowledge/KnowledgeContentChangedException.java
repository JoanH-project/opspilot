package com.opspilot.knowledge;

public class KnowledgeContentChangedException extends RuntimeException {
    public KnowledgeContentChangedException() {
        super("Knowledge sources changed while the answer was being generated; retry the request");
    }
}
