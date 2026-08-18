package com.opspilot.workspace.dto;

import java.time.Instant;
import com.opspilot.workspace.Workspace;
import com.opspilot.workspace.WorkspaceRole;

public record WorkspaceResponse(Long id, String name, WorkspaceOwnerResponse owner, WorkspaceRole role,
                                Instant createdAt, Instant updatedAt) {
    public static WorkspaceResponse from(Workspace workspace, WorkspaceRole role) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName(), WorkspaceOwnerResponse.from(workspace.getOwner()),
                role, workspace.getCreatedAt(), workspace.getUpdatedAt());
    }
}
