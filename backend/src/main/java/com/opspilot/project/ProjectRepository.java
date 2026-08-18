package com.opspilot.project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByWorkspace_IdAndStatusOrderByCreatedAtDesc(Long workspaceId, ProjectStatus status);
}
