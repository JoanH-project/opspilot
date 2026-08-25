package com.opspilot.activity;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    @EntityGraph(attributePaths = "actor")
    List<ActivityLog> findByWorkspace_IdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);
}
