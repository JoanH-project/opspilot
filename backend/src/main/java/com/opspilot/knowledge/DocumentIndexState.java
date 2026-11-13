package com.opspilot.knowledge;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_index_states")
public class DocumentIndexState {
    @Id
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private Long revision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentIndexStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "last_error_summary", length = 80)
    private String lastErrorSummary;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentIndexState() { }

    public DocumentIndexState(Long documentId, Long workspaceId, Long revision, Instant now) {
        this.documentId = documentId;
        this.workspaceId = workspaceId;
        this.revision = revision;
        this.status = DocumentIndexStatus.PENDING;
        this.updatedAt = now;
    }

    public void queue(Long currentRevision, Instant now) {
        revision = currentRevision;
        status = DocumentIndexStatus.PENDING;
        attempts = 0;
        nextAttemptAt = null;
        leaseUntil = null;
        claimToken = null;
        lastErrorSummary = null;
        updatedAt = now;
    }

    public void claim(Instant now, Instant leaseEnd, String claimToken) {
        status = DocumentIndexStatus.INDEXING;
        attempts++;
        leaseUntil = leaseEnd;
        this.claimToken = claimToken;
        nextAttemptAt = null;
        updatedAt = now;
    }

    public void ready(Instant now) {
        status = DocumentIndexStatus.READY;
        leaseUntil = null;
        claimToken = null;
        nextAttemptAt = null;
        lastErrorSummary = null;
        updatedAt = now;
    }

    public void failed(String summary, Instant nextAttemptAt, Instant now) {
        status = DocumentIndexStatus.FAILED;
        leaseUntil = null;
        claimToken = null;
        this.nextAttemptAt = nextAttemptAt;
        lastErrorSummary = summary;
        updatedAt = now;
    }

    public Long getDocumentId() { return documentId; }
    public Long getWorkspaceId() { return workspaceId; }
    public Long getRevision() { return revision; }
    public DocumentIndexStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public String getClaimToken() { return claimToken; }
    public String getLastErrorSummary() { return lastErrorSummary; }
    public Instant getUpdatedAt() { return updatedAt; }
}
