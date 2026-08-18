package com.opspilot.workspace;

import java.util.List;
import com.opspilot.workspace.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;
    public WorkspaceController(WorkspaceService workspaceService) { this.workspaceService = workspaceService; }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> createWorkspace(Authentication authentication,
                                                               @Valid @RequestBody CreateWorkspaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workspaceService.createWorkspace(currentUserId(authentication), request));
    }

    @GetMapping
    public List<WorkspaceSummaryResponse> listWorkspaces(Authentication authentication) {
        return workspaceService.listWorkspaces(currentUserId(authentication));
    }

    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(Authentication authentication, @PathVariable Long workspaceId) {
        return workspaceService.getWorkspace(workspaceId, currentUserId(authentication));
    }

    @PatchMapping("/{workspaceId}")
    public WorkspaceResponse updateWorkspace(Authentication authentication, @PathVariable Long workspaceId,
                                             @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.updateWorkspace(workspaceId, currentUserId(authentication), request);
    }

    @GetMapping("/{workspaceId}/members")
    public List<WorkspaceMemberResponse> listMembers(Authentication authentication, @PathVariable Long workspaceId) {
        return workspaceService.listMembers(workspaceId, currentUserId(authentication));
    }

    private Long currentUserId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
