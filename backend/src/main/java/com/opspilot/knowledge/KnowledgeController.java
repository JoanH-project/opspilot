package com.opspilot.knowledge;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/knowledge")
public class KnowledgeController {
    private final WorkspaceKnowledgeService knowledge;

    public KnowledgeController(WorkspaceKnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @PostMapping("/search")
    public KnowledgeSearchResponse search(Authentication authentication,
                                         @PathVariable Long workspaceId,
                                         @Valid @RequestBody KnowledgeQueryRequest request) {
        return knowledge.search(workspaceId, userId(authentication), request);
    }

    @PostMapping("/ask")
    public KnowledgeAnswerResponse ask(Authentication authentication,
                                       @PathVariable Long workspaceId,
                                       @Valid @RequestBody KnowledgeAskRequest request) {
        return knowledge.ask(workspaceId, userId(authentication), request);
    }

    @GetMapping("/index-status")
    public KnowledgeIndexStatusResponse indexStatus(Authentication authentication,
                                                   @PathVariable Long workspaceId) {
        return knowledge.indexStatus(workspaceId, userId(authentication));
    }

    @PostMapping("/reindex")
    public ResponseEntity<KnowledgeReindexResponse> reindex(Authentication authentication,
                                                            @PathVariable Long workspaceId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(knowledge.reindex(workspaceId, userId(authentication)));
    }

    private static Long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
