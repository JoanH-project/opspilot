package com.opspilot.project;

import java.util.List;
import java.util.Objects;

import com.opspilot.activity.ActivityLogService;
import com.opspilot.activity.ActivityType;
import com.opspilot.project.dto.*;
import com.opspilot.user.User;
import com.opspilot.user.UserRepository;
import com.opspilot.workspace.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccess;
    private final ActivityLogService activityLogService;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository,
                          WorkspaceAccessService workspaceAccess, ActivityLogService activityLogService) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.workspaceAccess = workspaceAccess;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public ProjectResponse create(Long workspaceId, Long userId, CreateProjectRequest request) {
        WorkspaceMember membership = workspaceAccess.requireMembership(workspaceId, userId);
        User creator = userRepository.findById(userId).orElseThrow(ProjectNotFoundException::new);
        Project project = new Project(membership.getWorkspace(), request.name().trim(),
                normalizeDescription(request.description()), creator);
        Project saved = projectRepository.saveAndFlush(project);
        activityLogService.record(saved.getWorkspace(), creator, ActivityType.PROJECT_CREATED, "PROJECT",
                saved.getId(), creator.getName() + " created project " + saved.getName());
        return ProjectResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Long workspaceId, Long userId, ProjectStatus status) {
        workspaceAccess.requireMembership(workspaceId, userId);
        return projectRepository.findByWorkspace_IdAndStatusOrderByCreatedAtDesc(workspaceId, status).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long projectId, Long userId) {
        Project project = findProject(projectId);
        workspaceAccess.requireMembership(project.getWorkspace().getId(), userId);
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse update(Long projectId, Long userId, UpdateProjectRequest request) {
        Project project = findProject(projectId);
        WorkspaceMember actorMembership = requireCanModify(project, userId);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ArchivedProjectException();
        }
        if (request.name() != null && request.name().isBlank()) {
            throw new InvalidProjectUpdateException("Project name must not be blank");
        }

        String name = request.name() == null ? null : request.name().trim();
        String description = normalizeDescription(request.description());
        boolean changed = (name != null && !Objects.equals(name, project.getName()))
                || (request.description() != null && !Objects.equals(description, project.getDescription()));
        if (!changed) {
            return ProjectResponse.from(project);
        }

        project.update(name, description);
        Project saved = projectRepository.saveAndFlush(project);
        User actor = actorMembership.getUser();
        activityLogService.record(saved.getWorkspace(), actor, ActivityType.PROJECT_UPDATED, "PROJECT",
                saved.getId(), actor.getName() + " updated project " + saved.getName());
        return ProjectResponse.from(saved);
    }

    @Transactional
    public ProjectResponse archive(Long projectId, Long userId) {
        Project project = findProject(projectId);
        WorkspaceMember actorMembership = requireCanModify(project, userId);
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            return ProjectResponse.from(project);
        }

        project.archive();
        Project saved = projectRepository.saveAndFlush(project);
        User actor = actorMembership.getUser();
        activityLogService.record(saved.getWorkspace(), actor, ActivityType.PROJECT_ARCHIVED, "PROJECT",
                saved.getId(), actor.getName() + " archived project " + saved.getName());
        return ProjectResponse.from(saved);
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId).orElseThrow(ProjectNotFoundException::new);
    }

    private WorkspaceMember requireCanModify(Project project, Long userId) {
        WorkspaceMember member = workspaceAccess.requireMembership(project.getWorkspace().getId(), userId);
        if (member.getRole() == WorkspaceRole.OWNER || member.getRole() == WorkspaceRole.ADMIN
                || project.getCreatedBy().getId().equals(userId)) {
            return member;
        }
        throw new ProjectAccessDeniedException();
    }

    private String normalizeDescription(String description) {
        return description == null ? null : description.trim();
    }
}
