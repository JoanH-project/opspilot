package com.opspilot.project;

import java.time.Instant;
import com.opspilot.user.User;
import com.opspilot.workspace.Workspace;
import jakarta.persistence.*;

@Entity
@Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "workspace_id", nullable = false) private Workspace workspace;
    @Column(nullable = false, length = 150) private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProjectStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private User createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected Project() { }
    public Project(Workspace workspace, String name, String description, User createdBy) {
        this.workspace=workspace; this.name=name; this.description=description; this.createdBy=createdBy; this.status=ProjectStatus.ACTIVE;
    }
    @PrePersist void created() { Instant now=Instant.now(); createdAt=now; updatedAt=now; }
    @PreUpdate void updated() { updatedAt=Instant.now(); }
    public void update(String name, String description) { if(name!=null)this.name=name; if(description!=null)this.description=description; }
    public void archive() { status=ProjectStatus.ARCHIVED; }
    public Long getId(){return id;} public Workspace getWorkspace(){return workspace;} public String getName(){return name;}
    public String getDescription(){return description;} public ProjectStatus getStatus(){return status;} public User getCreatedBy(){return createdBy;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
