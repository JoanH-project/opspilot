package com.opspilot.workspace.dto;

import java.time.Instant;
import com.opspilot.workspace.WorkspaceMember;
import com.opspilot.workspace.WorkspaceRole;

public record WorkspaceSummaryResponse(Long id, String name, WorkspaceRole role, Instant createdAt) {
    public static WorkspaceSummaryResponse from(WorkspaceMember member) {
        return new WorkspaceSummaryResponse(member.getWorkspace().getId(), member.getWorkspace().getName(),
                member.getRole(), member.getWorkspace().getCreatedAt());
    }
}
