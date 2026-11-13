package com.opspilot.knowledge;

import java.time.Clock;
import java.time.Instant;

import com.opspilot.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIndexStateService {
    private final DocumentIndexStateRepository states;
    private final Clock clock;

    public DocumentIndexStateService(DocumentIndexStateRepository states, Clock clock) {
        this.states = states;
        this.clock = clock;
    }

    @Transactional
    public void queue(Document document) {
        Instant now = clock.instant();
        long revision = document.getRevision() == null ? 0L : document.getRevision();
        DocumentIndexState state = states.findByIdForUpdate(document.getId())
                .orElseGet(() -> new DocumentIndexState(
                        document.getId(), document.getWorkspace().getId(), revision, now));
        state.queue(revision, now);
        states.save(state);
    }
}
