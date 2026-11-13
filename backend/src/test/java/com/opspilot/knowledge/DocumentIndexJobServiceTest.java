package com.opspilot.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.opspilot.document.Document;
import com.opspilot.document.DocumentRepository;
import com.opspilot.document.DocumentStatus;
import com.opspilot.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class DocumentIndexJobServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    @Test
    void retriesWithBoundedBackoffAndStopsAfterMaximumAttempts() {
        DocumentIndexStateRepository states = mock(DocumentIndexStateRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentIndexState state = new DocumentIndexState(41L, 7L, 0L, NOW);
        Document document = mock(Document.class);
        Workspace workspace = mock(Workspace.class);
        when(states.findByIdForUpdate(41L)).thenReturn(Optional.of(state));
        when(documents.findById(41L)).thenReturn(Optional.of(document));
        when(document.getWorkspace()).thenReturn(workspace);
        when(workspace.getId()).thenReturn(7L);
        when(document.getRevision()).thenReturn(0L);

        DocumentIndexJobService service = new DocumentIndexJobService(
                states, documents, Clock.fixed(NOW, ZoneOffset.UTC));
        String claimToken = "attempt-1";
        state.claim(NOW, NOW.plusSeconds(90), claimToken);
        IndexJob job = new IndexJob(41L, 7L, 0L, DocumentStatus.ACTIVE, "Runbook", "Text", claimToken, 1);
        service.markFailed(job);
        assertEquals(DocumentIndexStatus.FAILED, state.getStatus());
        assertEquals(NOW.plusSeconds(5), state.getNextAttemptAt());

        for (int attempt = 2; attempt <= DocumentIndexJobService.MAX_ATTEMPTS; attempt++) {
            String retryClaimToken = "attempt-" + attempt;
            state.claim(NOW, NOW.plusSeconds(90), retryClaimToken);
            service.markFailed(new IndexJob(
                    41L, 7L, 0L, DocumentStatus.ACTIVE, "Runbook", "Text", retryClaimToken, attempt));
        }
        assertEquals(DocumentIndexJobService.MAX_ATTEMPTS, state.getAttempts());
        assertEquals(DocumentIndexStatus.FAILED, state.getStatus());
        assertNull(state.getNextAttemptAt());
        assertEquals("INDEXING_FAILED", state.getLastErrorSummary());
        assertNull(state.getClaimToken());
    }

    @Test
    void staleWorkerCannotOverwriteAReclaimedAttempt() {
        DocumentIndexStateRepository states = mock(DocumentIndexStateRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentIndexState state = new DocumentIndexState(41L, 7L, 0L, NOW);
        state.claim(NOW.minusSeconds(100), NOW.minusSeconds(10), "expired-claim");
        IndexJob staleJob = new IndexJob(
                41L, 7L, 0L, DocumentStatus.ACTIVE, "Runbook", "Text", "expired-claim", 1);

        state.queue(0L, NOW.minusSeconds(5));
        state.claim(NOW, NOW.plusSeconds(90), "current-claim");
        when(states.findByIdForUpdate(41L)).thenReturn(Optional.of(state));

        DocumentIndexJobService service = new DocumentIndexJobService(
                states, documents, Clock.fixed(NOW, ZoneOffset.UTC));
        service.markReady(staleJob);

        assertEquals(DocumentIndexStatus.INDEXING, state.getStatus());
        assertEquals("current-claim", state.getClaimToken());
        assertEquals(1, state.getAttempts());
        verify(documents, never()).findById(41L);
    }

    @Test
    void expiredEighthLeaseBecomesTerminalWithoutClaimingAttemptNine() {
        DocumentIndexStateRepository states = mock(DocumentIndexStateRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentIndexState state = new DocumentIndexState(41L, 7L, 0L, NOW);
        for (int attempt = 1; attempt <= DocumentIndexJobService.MAX_ATTEMPTS; attempt++) {
            state.claim(NOW.minusSeconds(100), NOW.minusSeconds(1), "claim-" + attempt);
        }
        Document document = mock(Document.class);
        Workspace workspace = mock(Workspace.class);
        when(states.lockNextClaimable(
                org.mockito.ArgumentMatchers.eq(NOW),
                org.mockito.ArgumentMatchers.eq(DocumentIndexJobService.MAX_ATTEMPTS),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(state));
        when(documents.findById(41L)).thenReturn(Optional.of(document));
        when(document.getWorkspace()).thenReturn(workspace);
        when(workspace.getId()).thenReturn(7L);
        when(document.getRevision()).thenReturn(0L);

        DocumentIndexJobService service = new DocumentIndexJobService(
                states, documents, Clock.fixed(NOW, ZoneOffset.UTC));
        Optional<IndexJob> claimed = service.claimNext();

        assertFalse(claimed.isPresent());
        assertEquals(DocumentIndexStatus.FAILED, state.getStatus());
        assertEquals(DocumentIndexJobService.MAX_ATTEMPTS, state.getAttempts());
        assertNull(state.getNextAttemptAt());
        assertNull(state.getLeaseUntil());
        assertNull(state.getClaimToken());
        assertEquals("INDEXING_FAILED", state.getLastErrorSummary());
    }
}
