package com.opspilot.workspace;

public class InsufficientWorkspaceRoleException extends RuntimeException {
    public InsufficientWorkspaceRoleException() { super("Insufficient workspace permissions"); }
}
