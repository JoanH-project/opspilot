package com.opspilot.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(@NotBlank(message = "Workspace name is required")
                                     @Size(max = 100, message = "Workspace name must not exceed 100 characters")
                                     String name) { }
