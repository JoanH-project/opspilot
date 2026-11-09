package com.opspilot.knowledge;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.opspilot.document.Document;
import com.opspilot.document.DocumentRepository;
import com.opspilot.document.DocumentStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIndexJobService {
    public static final int MAX_ATTEMPTS = 8;
    private static final long LEASE_SECONDS = 90;

    private final DocumentIndexStateRepository states;
    private final DocumentRepository documents;
    private final Clock clock;

    public DocumentIndexJobService(DocumentIndexStateRepository states, DocumentRepository documents, Clock clock) {
        this.states = states;
        this.documents = documents;
        this.clock = clock;
    }

    @Transactional
    public Optional<IndexJob> claimNext() {
        Instant now = clock.instant();
        return states.lockNextClaimable(now, MAX_ATTEMPTS, PageRequest.of(0, 1)).stream().findFirst()
                .flatMap(state -> {
                    Optional<Document> found = documents.findById(state.getDocumentId());
                    if (found.isEmpty()) {
                        return Optional.empty();
                    }
                    Document document = found.get();
                    long revision = currentRevision(document);
                    if (!state.getWorkspaceId().equals(document.getWorkspace().getId())
                            || state.getRevision() != revision) {
                        state.queue(revision, now);
                        return Optional.empty();
                    }
                    state.claim(now, now.plusSeconds(LEASE_SECONDS));
                    return Optional.of(new IndexJob(
                            document.getId(), document.getWorkspace().getId(), revision,
                            document.getStatus(), document.getTitle(), document.getContent(), state.getAttempts()));
                });
    }

    @Transactional
    public void markReady(IndexJob job) {
        updateAfterJob(job, null);
    }

    @Transactional
    public void markFailed(IndexJob job) {
        updateAfterJob(job, "INDEXING_FAILED");
    }

    private void updateAfterJob(IndexJob job, String failureSummary) {
        Instant now = clock.instant();
        states.findById(job.documentId()).ifPresent(state -> {
            Optional<Document> found = documents.findById(job.documentId());
            if (found.isEmpty()) {
                states.delete(state);
                return;
            }
            Document document = found.get();
            long currentRevision = currentRevision(document);
            if (!state.getWorkspaceId().equals(document.getWorkspace().getId())
                    || state.getRevision() != job.revision()
                    || currentRevision != job.revision()) {
                state.queue(currentRevision, now);
                return;
            }
            if (failureSummary == null) {
                state.ready(now);
                return;
            }
            Instant nextAttemptAt = state.getAttempts() >= MAX_ATTEMPTS
                    ? null
                    : now.plusSeconds(Math.min(300, 5L << Math.max(0, state.getAttempts() - 1)));
            state.failed(failureSummary, nextAttemptAt, now);
        });
    }

    private static long currentRevision(Document document) {
        return document.getRevision() == null ? 0L : document.getRevision();
    }
}
