package com.opspilot.knowledge;

public record KnowledgeReference(
        Long documentId,
        Long workspaceId,
        long revision,
        String title,
        String chunkId,
        int chunkIndex,
        String excerpt,
        double score,
        int sourceStart,
        int sourceEnd,
        String href) { }
