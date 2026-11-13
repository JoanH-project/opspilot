package com.opspilot.knowledge;

import com.opspilot.document.DocumentStatus;

public record IndexJob(
        Long documentId,
        Long workspaceId,
        long revision,
        DocumentStatus documentStatus,
        String title,
        String content,
        String claimToken,
        int attempts) { }
