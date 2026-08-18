package com.opspilot.user;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 320)
    private String email;
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() { }
    public User(String email, String passwordHash, String name) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
    }
    @PrePersist void setCreationTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void setUpdateTimestamp() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
