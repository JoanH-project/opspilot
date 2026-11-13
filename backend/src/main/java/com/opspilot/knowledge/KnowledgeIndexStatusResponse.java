package com.opspilot.knowledge;

public record KnowledgeIndexStatusResponse(long total, long pending, long indexing, long ready, long failed) { }
