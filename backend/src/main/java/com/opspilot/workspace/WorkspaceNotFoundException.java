package com.opspilot.workspace;

public class WorkspaceNotFoundException extends RuntimeException {
    public WorkspaceNotFoundException() { super("Workspace not found"); }
}
