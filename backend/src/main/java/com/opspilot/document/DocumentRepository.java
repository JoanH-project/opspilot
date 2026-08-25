package com.opspilot.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByWorkspace_IdAndStatusOrderByCreatedAtDesc(Long workspaceId, DocumentStatus status);

    long countByWorkspace_IdAndStatus(Long workspaceId, DocumentStatus status);
}
