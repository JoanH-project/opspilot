package com.opspilot.workspace;

import java.time.Instant;
import com.opspilot.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "workspace_members", uniqueConstraints =
        @UniqueConstraint(name = "uk_workspace_members_workspace_user", columnNames = {"workspace_id", "user_id"}))
public class WorkspaceMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected WorkspaceMember() { }
    public WorkspaceMember(Workspace workspace, User user, WorkspaceRole role) {
        this.workspace = workspace; this.user = user; this.role = role;
    }
    @PrePersist void setJoinedAt() { joinedAt = Instant.now(); }
    public Long getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public User getUser() { return user; }
    public WorkspaceRole getRole() { return role; }
    public Instant getJoinedAt() { return joinedAt; }
}
