package com.opspilot.dashboard;

import java.time.Clock;
import java.time.LocalDate;

import com.opspilot.activity.ActivityLogService;
import com.opspilot.dashboard.dto.WorkspaceDashboardResponse;
import com.opspilot.document.DocumentRepository;
import com.opspilot.document.DocumentStatus;
import com.opspilot.project.ProjectRepository;
import com.opspilot.project.ProjectStatus;
import com.opspilot.task.TaskRepository;
import com.opspilot.task.TaskStatus;
import com.opspilot.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final DocumentRepository documents;
    private final WorkspaceAccessService workspaceAccess;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public DashboardService(ProjectRepository projects, TaskRepository tasks,
                            DocumentRepository documents, WorkspaceAccessService workspaceAccess,
                            ActivityLogService activityLogService, Clock clock) {
        this.projects = projects;
        this.tasks = tasks;
        this.documents = documents;
        this.workspaceAccess = workspaceAccess;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkspaceDashboardResponse dashboard(Long workspaceId, Long userId) {
        workspaceAccess.requireMembership(workspaceId, userId);

        var projectCounts = new WorkspaceDashboardResponse.Projects(
                projects.countByWorkspace_IdAndStatus(workspaceId, ProjectStatus.ACTIVE),
                projects.countByWorkspace_IdAndStatus(workspaceId, ProjectStatus.ARCHIVED));
        var taskCounts = new WorkspaceDashboardResponse.Tasks(
                tasks.countForWorkspace(workspaceId),
                tasks.countForWorkspaceByStatus(workspaceId, TaskStatus.TODO),
                tasks.countForWorkspaceByStatus(workspaceId, TaskStatus.IN_PROGRESS),
                tasks.countForWorkspaceByStatus(workspaceId, TaskStatus.DONE),
                tasks.countOverdueForWorkspace(workspaceId, LocalDate.now(clock)));
        var documentCounts = new WorkspaceDashboardResponse.Documents(
                documents.countByWorkspace_IdAndStatus(workspaceId, DocumentStatus.ACTIVE),
                documents.countByWorkspace_IdAndStatus(workspaceId, DocumentStatus.ARCHIVED));

        return new WorkspaceDashboardResponse(
                workspaceId,
                projectCounts,
                taskCounts,
                documentCounts,
                activityLogService.feed(workspaceId, userId, ActivityLogService.DEFAULT_LIMIT));
    }
}
