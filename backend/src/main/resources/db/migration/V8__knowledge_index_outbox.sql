ALTER TABLE documents
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 1;

CREATE TABLE document_index_states (
    document_id BIGINT NOT NULL,
    workspace_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    lease_until DATETIME(6) NULL,
    last_error_summary VARCHAR(80) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (document_id),
    KEY idx_document_index_claim (status, next_attempt_at, lease_until, updated_at),
    KEY idx_document_index_workspace_status (workspace_id, status),
    CONSTRAINT fk_document_index_state_document
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_index_state_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT chk_document_index_state_status
        CHECK (status IN ('PENDING', 'INDEXING', 'READY', 'FAILED'))
);

CREATE TABLE workspace_knowledge_reindex_limits (
    workspace_id BIGINT NOT NULL,
    last_requested_at DATETIME(6) NOT NULL,
    PRIMARY KEY (workspace_id),
    CONSTRAINT fk_workspace_knowledge_reindex_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE
);

INSERT INTO document_index_states (
    document_id, workspace_id, revision, status, attempts, updated_at
)
SELECT id, workspace_id, revision, 'PENDING', 0, UTC_TIMESTAMP(6)
FROM documents
WHERE status = 'ACTIVE';
