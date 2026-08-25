package com.opspilot.activity;

import java.time.Instant;

import com.opspilot.user.User;
import com.opspilot.workspace.Workspace;
import jakarta.persistence.*;

@Entity
@Table(name = "activity_logs")
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 50)
    private ActivityType type;
    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @Column(nullable = false, length = 500)
    private String message;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityLog() {
    }

    public ActivityLog(Workspace workspace, User actor, ActivityType type,
                       String entityType, Long entityId, String message) {
        this.workspace = workspace;
        this.actor = actor;
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.message = message;
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public User getActor() { return actor; }
    public ActivityType getType() { return type; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
