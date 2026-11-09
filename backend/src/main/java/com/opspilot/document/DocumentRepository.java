package com.opspilot.document;

import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByWorkspace_IdAndStatusOrderByCreatedAtDesc(Long workspaceId, DocumentStatus status);

    long countByWorkspace_IdAndStatus(Long workspaceId, DocumentStatus status);

    List<Document> findAllByIdInAndWorkspace_IdAndStatus(
            Collection<Long> ids, Long workspaceId, DocumentStatus status);
}
