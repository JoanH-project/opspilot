package com.opspilot.workspace;

import java.time.Instant;
import com.opspilot.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "workspaces")
public class Workspace {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Workspace() { }
    public Workspace(String name, User owner) { this.name = name; this.owner = owner; }
    @PrePersist void setCreationTimestamps() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void setUpdateTimestamp() { updatedAt = Instant.now(); }
    public void rename(String name) { this.name = name; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public User getOwner() { return owner; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
