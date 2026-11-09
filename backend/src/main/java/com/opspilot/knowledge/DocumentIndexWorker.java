package com.opspilot.knowledge;

import java.util.Optional;

import com.opspilot.document.DocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.knowledge.index-worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DocumentIndexWorker {
    private static final Logger logger = LoggerFactory.getLogger(DocumentIndexWorker.class);
    private final DocumentIndexJobService jobs;
    private final KnowledgeAiClient aiClient;

    public DocumentIndexWorker(DocumentIndexJobService jobs, KnowledgeAiClient aiClient) {
        this.jobs = jobs;
        this.aiClient = aiClient;
    }

    @Scheduled(fixedDelayString = "${app.knowledge.index-worker.poll-interval:2000}")
    public void processOne() {
        Optional<IndexJob> claimed = jobs.claimNext();
        if (claimed.isEmpty()) {
            return;
        }
        IndexJob job = claimed.get();
        try {
            if (job.documentStatus() == DocumentStatus.ACTIVE) {
                KnowledgeAiClient.IndexResponse response = aiClient.index(
                        job.workspaceId(), job.documentId(), job.revision(), job.title(), job.content());
                if (response == null || response.revision() != job.revision()) {
                    throw new KnowledgeAiUnavailableException();
                }
            } else {
                aiClient.archive(job.workspaceId(), job.documentId(), job.revision());
            }
            jobs.markReady(job);
        } catch (RuntimeException exception) {
            logger.warn("knowledge_index_failed document_id={} failure_type={}",
                    job.documentId(), exception.getClass().getSimpleName());
            jobs.markFailed(job);
        }
    }

}
