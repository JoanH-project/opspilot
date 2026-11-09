package com.opspilot.knowledge;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.opspilot.document.Document;
import com.opspilot.document.DocumentRepository;
import com.opspilot.document.DocumentStatus;
import com.opspilot.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceKnowledgeService {
    private static final int MAX_REINDEX_DOCUMENTS = 500;
    private static final int INTERNAL_CONTEXT_LIMIT = 8;

    private final WorkspaceAccessService access;
    private final DocumentRepository documents;
    private final DocumentIndexStateRepository indexStates;
    private final DocumentIndexStateService indexStateService;
    private final WorkspaceReindexLimiter reindexLimiter;
    private final KnowledgeAiClient aiClient;
    private final Clock clock;

    public WorkspaceKnowledgeService(
            WorkspaceAccessService access,
            DocumentRepository documents,
            DocumentIndexStateRepository indexStates,
            DocumentIndexStateService indexStateService,
            WorkspaceReindexLimiter reindexLimiter,
            KnowledgeAiClient aiClient,
            Clock clock) {
        this.access = access;
        this.documents = documents;
        this.indexStates = indexStates;
        this.indexStateService = indexStateService;
        this.reindexLimiter = reindexLimiter;
        this.aiClient = aiClient;
        this.clock = clock;
    }

    public KnowledgeSearchResponse search(Long workspaceId, Long userId, KnowledgeQueryRequest request) {
        access.requireMembership(workspaceId, userId);
        KnowledgeAiClient.SearchResponse response = aiClient.search(
                workspaceId, request.query().trim(), request.safeLimit());
        access.requireMembership(workspaceId, userId);
        return new KnowledgeSearchResponse(validateCurrent(workspaceId, response.results()));
    }

    public KnowledgeAnswerResponse ask(Long workspaceId, Long userId, KnowledgeAskRequest request) {
        access.requireMembership(workspaceId, userId);
        KnowledgeAiClient.SearchResponse response = aiClient.search(workspaceId, request.question().trim(), 5);
        List<KnowledgeReference> evidence = validateCurrent(workspaceId, response.results());
        if (evidence.isEmpty()) {
            return insufficientEvidence();
        }

        List<KnowledgeAiClient.AiEvidence> verifiedEvidence = evidence.stream()
                .limit(INTERNAL_CONTEXT_LIMIT)
                .map(source -> new KnowledgeAiClient.AiEvidence(
                        source.workspaceId(), source.documentId(), source.revision(), source.title(),
                        source.chunkId(), source.excerpt()))
                .toList();
        KnowledgeAiClient.AnswerResponse generated = aiClient.answer(request.question().trim(), verifiedEvidence);
        if (generated == null || generated.citedChunkIds() == null || generated.citedChunkIds().isEmpty()) {
            return insufficientEvidence();
        }

        Map<String, KnowledgeReference> sourceByChunk = evidence.stream()
                .collect(Collectors.toMap(KnowledgeReference::chunkId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        if (generated.citedChunkIds().stream().anyMatch(chunkId -> !sourceByChunk.containsKey(chunkId))) {
            return insufficientEvidence();
        }

        access.requireMembership(workspaceId, userId);
        verifyStillCurrent(workspaceId, evidence);
        List<KnowledgeReference> cited = generated.citedChunkIds().stream().distinct()
                .map(sourceByChunk::get).toList();
        if (!"ANSWERED".equals(generated.status()) || generated.answer() == null || generated.answer().isBlank()) {
            return insufficientEvidence();
        }
        return new KnowledgeAnswerResponse("ANSWERED", generated.answer(), cited);
    }

    @Transactional(readOnly = true)
    public KnowledgeIndexStatusResponse indexStatus(Long workspaceId, Long userId) {
        access.requireMembership(workspaceId, userId);
        return new KnowledgeIndexStatusResponse(
                indexStates.countByWorkspaceId(workspaceId),
                count(workspaceId, DocumentIndexStatus.PENDING),
                count(workspaceId, DocumentIndexStatus.INDEXING),
                count(workspaceId, DocumentIndexStatus.READY),
                count(workspaceId, DocumentIndexStatus.FAILED));
    }

    @Transactional
    public KnowledgeReindexResponse reindex(Long workspaceId, Long userId) {
        access.requireAdminOrOwner(workspaceId, userId);
        long activeCount = documents.countByWorkspace_IdAndStatus(workspaceId, DocumentStatus.ACTIVE);
        if (activeCount > MAX_REINDEX_DOCUMENTS) {
            throw new KnowledgeReindexTooLargeException();
        }
        if (!reindexLimiter.tryAcquire(workspaceId)) {
            throw new KnowledgeReindexRateLimitException(300);
        }
        List<Document> activeDocuments = documents.findByWorkspace_IdAndStatusOrderByCreatedAtDesc(
                workspaceId, DocumentStatus.ACTIVE);
        activeDocuments.forEach(indexStateService::queue);
        return new KnowledgeReindexResponse(activeDocuments.size(), "QUEUED");
    }

    private List<KnowledgeReference> validateCurrent(Long workspaceId, List<KnowledgeAiClient.AiSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Collection<Long> ids = hits.stream()
                .filter(hit -> hit.workspaceId() != null && hit.workspaceId().equals(workspaceId))
                .map(KnowledgeAiClient.AiSearchHit::documentId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Document> current = documents.findAllByIdInAndWorkspace_IdAndStatus(
                        ids, workspaceId, DocumentStatus.ACTIVE).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        List<KnowledgeReference> references = new ArrayList<>();
        for (KnowledgeAiClient.AiSearchHit hit : hits) {
            if (!workspaceId.equals(hit.workspaceId())) continue;
            Document document = current.get(hit.documentId());
            if (document == null || currentRevision(document) != hit.revision()) continue;
            String excerpt = boundedExcerpt(hit.text());
            if (excerpt.isBlank()) continue;
            int contentLength = document.getContent().length();
            references.add(new KnowledgeReference(
                    document.getId(), workspaceId, currentRevision(document), document.getTitle(),
                    hit.chunkId(), hit.chunkIndex(), excerpt, hit.score(),
                    Math.max(0, Math.min(hit.sourceStart(), contentLength)),
                    Math.max(0, Math.min(hit.sourceEnd(), contentLength)),
                    "/app/" + workspaceId + "/documents/" + document.getId()));
        }
        return references;
    }

    private void verifyStillCurrent(Long workspaceId, List<KnowledgeReference> evidence) {
        List<Long> ids = evidence.stream().map(KnowledgeReference::documentId).distinct().toList();
        Map<Long, Document> current = documents.findAllByIdInAndWorkspace_IdAndStatus(
                        ids, workspaceId, DocumentStatus.ACTIVE).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        boolean changed = evidence.stream().anyMatch(source -> {
            Document document = current.get(source.documentId());
            return document == null || currentRevision(document) != source.revision();
        });
        if (changed) {
            throw new KnowledgeContentChangedException();
        }
    }

    private long count(Long workspaceId, DocumentIndexStatus status) {
        return indexStates.countByWorkspaceAndStatus(workspaceId, status);
    }

    private static long currentRevision(Document document) {
        return document.getRevision() == null ? 0L : document.getRevision();
    }

    private static String boundedExcerpt(String text) {
        if (text == null) return "";
        String normalized = text.strip();
        return normalized.length() <= 3000 ? normalized : normalized.substring(0, 3000);
    }

    private static KnowledgeAnswerResponse insufficientEvidence() {
        return new KnowledgeAnswerResponse(
                "INSUFFICIENT_EVIDENCE",
                "The current workspace documents do not contain enough verified evidence to answer this question.",
                List.of());
    }
}
