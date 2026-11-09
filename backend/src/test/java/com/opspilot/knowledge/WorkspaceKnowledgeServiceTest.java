package com.opspilot.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.opspilot.document.Document;
import com.opspilot.document.DocumentRepository;
import com.opspilot.document.DocumentStatus;
import com.opspilot.knowledge.KnowledgeAiClient.AiSearchHit;
import com.opspilot.knowledge.KnowledgeAiClient.AnswerResponse;
import com.opspilot.knowledge.KnowledgeAiClient.SearchResponse;
import com.opspilot.workspace.InsufficientWorkspaceRoleException;
import com.opspilot.workspace.Workspace;
import com.opspilot.workspace.WorkspaceAccessService;
import com.opspilot.workspace.WorkspaceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkspaceKnowledgeServiceTest {
    private final WorkspaceAccessService access = mock(WorkspaceAccessService.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentIndexStateRepository states = mock(DocumentIndexStateRepository.class);
    private final DocumentIndexStateService stateService = mock(DocumentIndexStateService.class);
    private final WorkspaceReindexLimiter limiter = mock(WorkspaceReindexLimiter.class);
    private final KnowledgeAiClient ai = mock(KnowledgeAiClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
    private final WorkspaceKnowledgeService service = new WorkspaceKnowledgeService(
            access, documents, states, stateService, limiter, ai, clock);

    @BeforeEach
    void setUp() {
        when(states.countByWorkspaceId(7L)).thenReturn(3L);
        when(states.countByWorkspaceAndStatus(eq(7L), any())).thenReturn(0L);
    }

    @Test
    void searchReturnsOnlyActiveCurrentRevisionFromTheAuthorizedWorkspace() throws Exception {
        Document current = document(41L, 7L, 3L, DocumentStatus.ACTIVE);
        AiSearchHit currentHit = hit(7L, 41L, 3L, "chunk-current");
        AiSearchHit staleHit = hit(7L, 41L, 2L, "chunk-stale");
        AiSearchHit crossWorkspaceHit = hit(8L, 41L, 3L, "chunk-cross-workspace");
        when(ai.search(7L, "restart", 5)).thenReturn(new SearchResponse(
                List.of(currentHit, staleHit, crossWorkspaceHit)));
        when(documents.findAllByIdInAndWorkspace_IdAndStatus(List.of(41L), 7L, DocumentStatus.ACTIVE))
                .thenReturn(List.of(current));

        KnowledgeSearchResponse result = service.search(7L, 5L, new KnowledgeQueryRequest("restart", null));

        assertEquals(1, result.results().size());
        assertEquals("chunk-current", result.results().getFirst().chunkId());
        assertEquals("Current title", result.results().getFirst().title());
        verify(access, times(2)).requireMembership(7L, 5L);
    }

    @Test
    void askRechecksTheWorkspaceAndDocumentRevisionAfterGeneration() throws Exception {
        Document before = document(41L, 7L, 3L, DocumentStatus.ACTIVE);
        AiSearchHit hit = hit(7L, 41L, 3L, "chunk-current");
        when(ai.search(7L, "how to restart", 5)).thenReturn(new SearchResponse(List.of(hit)));
        when(ai.answer(eq("how to restart"), any())).thenReturn(
                new AnswerResponse("ANSWERED", "Restart the service.", List.of("chunk-current")));
        when(documents.findAllByIdInAndWorkspace_IdAndStatus(List.of(41L), 7L, DocumentStatus.ACTIVE))
                .thenReturn(List.of(before), List.of(before));

        KnowledgeAnswerResponse result = service.ask(7L, 5L, new KnowledgeAskRequest("how to restart"));

        assertEquals("ANSWERED", result.status());
        assertEquals("Restart the service.", result.answer());
        assertEquals("chunk-current", result.sources().getFirst().chunkId());
        verify(access, times(2)).requireMembership(7L, 5L);
    }

    @Test
    void askRejectsAnAnswerWhenItsDocumentChangesDuringGeneration() throws Exception {
        Document before = document(41L, 7L, 3L, DocumentStatus.ACTIVE);
        Document after = document(41L, 7L, 4L, DocumentStatus.ACTIVE);
        when(ai.search(7L, "question", 5)).thenReturn(new SearchResponse(List.of(hit(7L, 41L, 3L, "chunk"))));
        when(ai.answer(eq("question"), any())).thenReturn(
                new AnswerResponse("ANSWERED", "An answer based on the previous revision.", List.of("chunk")));
        when(documents.findAllByIdInAndWorkspace_IdAndStatus(List.of(41L), 7L, DocumentStatus.ACTIVE))
                .thenReturn(List.of(before), List.of(after));

        assertThrows(KnowledgeContentChangedException.class,
                () -> service.ask(7L, 5L, new KnowledgeAskRequest("question")));
    }

    @Test
    void nonMemberCannotReachTheAiService() {
        when(access.requireMembership(7L, 99L)).thenThrow(new WorkspaceNotFoundException());

        assertThrows(WorkspaceNotFoundException.class,
                () -> service.search(7L, 99L, new KnowledgeQueryRequest("secret", 5)));
        verify(ai, never()).search(eq(7L), eq("secret"), eq(5));
    }

    @Test
    void ownerCannotReindexMoreThanFiveHundredActiveDocuments() {
        when(access.requireAdminOrOwner(7L, 5L)).thenReturn(null);
        when(documents.countByWorkspace_IdAndStatus(7L, DocumentStatus.ACTIVE)).thenReturn(501L);

        assertThrows(KnowledgeReindexTooLargeException.class, () -> service.reindex(7L, 5L));
        verify(limiter, never()).tryAcquire(any());
    }

    @Test
    void memberCannotTriggerReindexAndOwnerQueuesOnlyActiveDocuments() throws Exception {
        when(access.requireAdminOrOwner(7L, 6L)).thenThrow(new InsufficientWorkspaceRoleException());
        assertThrows(InsufficientWorkspaceRoleException.class, () -> service.reindex(7L, 6L));
        verify(limiter, never()).tryAcquire(any());

        Document first = document(41L, 7L, 3L, DocumentStatus.ACTIVE);
        Document second = document(42L, 7L, 1L, DocumentStatus.ACTIVE);
        when(access.requireAdminOrOwner(7L, 5L)).thenReturn(null);
        when(documents.countByWorkspace_IdAndStatus(7L, DocumentStatus.ACTIVE)).thenReturn(2L);
        when(limiter.tryAcquire(7L)).thenReturn(true);
        when(documents.findByWorkspace_IdAndStatusOrderByCreatedAtDesc(7L, DocumentStatus.ACTIVE))
                .thenReturn(List.of(first, second));

        KnowledgeReindexResponse result = service.reindex(7L, 5L);

        assertEquals(2, result.queuedDocuments());
        assertEquals("QUEUED", result.status());
        verify(stateService).queue(first);
        verify(stateService).queue(second);
    }

    private static AiSearchHit hit(Long workspaceId, Long documentId, long revision, String chunkId) {
        return new AiSearchHit(workspaceId, documentId, revision, "Untrusted title", chunkId,
                0, "Restart the service from its control panel.", 0.91, 0, 42);
    }

    private static Document document(Long documentId, Long workspaceId, Long revision,
                                     DocumentStatus status) throws Exception {
        Workspace workspace = new Workspace("Workspace", null);
        set(workspace, "id", workspaceId);
        Document document = new Document(workspace, "Current title", "Current source text", null);
        set(document, "id", documentId);
        set(document, "revision", revision);
        set(document, "status", status);
        return document;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
