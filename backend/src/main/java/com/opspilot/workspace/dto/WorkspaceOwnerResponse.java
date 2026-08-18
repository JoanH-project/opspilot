package com.opspilot.workspace.dto;

import com.opspilot.user.User;

public record WorkspaceOwnerResponse(Long id, String email, String name) {
    public static WorkspaceOwnerResponse from(User user) {
        return new WorkspaceOwnerResponse(user.getId(), user.getEmail(), user.getName());
    }
}
