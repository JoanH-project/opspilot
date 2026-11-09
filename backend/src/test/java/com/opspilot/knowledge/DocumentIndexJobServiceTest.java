package com.opspilot.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.opspilot.document.Document;
import com.opspilot.document.DocumentRepository;
import com.opspilot.document.DocumentStatus;
import com.opspilot.workspace.Workspace;
import org.junit.jupiter.api.Test;

class DocumentIndexJobServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    @Test
    void retriesWithBoundedBackoffAndStopsAfterMaximumAttempts() {
        DocumentIndexStateRepository states = mock(DocumentIndexStateRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentIndexState state = new DocumentIndexState(41L, 7L, 0L, NOW);
        Document document = mock(Document.class);
        Workspace workspace = mock(Workspace.class);
        when(states.findById(41L)).thenReturn(Optional.of(state));
        when(documents.findById(41L)).thenReturn(Optional.of(document));
        when(document.getWorkspace()).thenReturn(workspace);
        when(workspace.getId()).thenReturn(7L);
        when(document.getRevision()).thenReturn(0L);

        DocumentIndexJobService service = new DocumentIndexJobService(
                states, documents, Clock.fixed(NOW, ZoneOffset.UTC));
        IndexJob job = new IndexJob(41L, 7L, 0L, DocumentStatus.ACTIVE, "Runbook", "Text", 1);

        state.claim(NOW, NOW.plusSeconds(90));
        service.markFailed(job);
        assertEquals(DocumentIndexStatus.FAILED, state.getStatus());
        assertEquals(NOW.plusSeconds(5), state.getNextAttemptAt());

        for (int attempt = 2; attempt <= DocumentIndexJobService.MAX_ATTEMPTS; attempt++) {
            state.claim(NOW, NOW.plusSeconds(90));
            service.markFailed(job);
        }
        assertEquals(DocumentIndexJobService.MAX_ATTEMPTS, state.getAttempts());
        assertEquals(DocumentIndexStatus.FAILED, state.getStatus());
        assertNull(state.getNextAttemptAt());
        assertEquals("INDEXING_FAILED", state.getLastErrorSummary());
    }
}
