package com.opspilot.workspace.dto;

import java.time.Instant;
import com.opspilot.workspace.WorkspaceMember;
import com.opspilot.workspace.WorkspaceRole;

public record WorkspaceMemberResponse(WorkspaceOwnerResponse user, WorkspaceRole role, Instant joinedAt) {
    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        return new WorkspaceMemberResponse(WorkspaceOwnerResponse.from(member.getUser()), member.getRole(), member.getJoinedAt());
    }
}
