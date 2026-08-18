package com.opspilot.project.dto;
import java.time.Instant;
import com.opspilot.project.Project;
import com.opspilot.project.ProjectStatus;
public record ProjectResponse(Long id,Long workspaceId,String name,String description,ProjectStatus status,ProjectCreatorResponse creator,Instant createdAt,Instant updatedAt) {
 public static ProjectResponse from(Project p){return new ProjectResponse(p.getId(),p.getWorkspace().getId(),p.getName(),p.getDescription(),p.getStatus(),ProjectCreatorResponse.from(p.getCreatedBy()),p.getCreatedAt(),p.getUpdatedAt());}
}
