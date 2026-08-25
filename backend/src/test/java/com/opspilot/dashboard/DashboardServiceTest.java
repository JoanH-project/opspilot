package com.opspilot.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.opspilot.activity.ActivityLogService;
import com.opspilot.activity.dto.ActivityResponse;
import com.opspilot.dashboard.dto.WorkspaceDashboardResponse;
import com.opspilot.document.DocumentRepository;
import com.opspilot.document.DocumentStatus;
import com.opspilot.project.ProjectRepository;
import com.opspilot.project.ProjectStatus;
import com.opspilot.task.TaskRepository;
import com.opspilot.task.TaskStatus;
import com.opspilot.workspace.WorkspaceAccessService;
import com.opspilot.workspace.WorkspaceNotFoundException;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    private static final Long WORKSPACE_ID = 3L;
    private static final Long USER_ID = 7L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-25T15:30:00Z"), ZoneOffset.UTC);

    @Test
    void returnsWorkspaceAggregateCountsAndRecentActivities() {
        ProjectRepository projects = mock(ProjectRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        WorkspaceAccessService access = mock(WorkspaceAccessService.class);
        ActivityLogService activities = mock(ActivityLogService.class);
        List<ActivityResponse> recent = List.of(mock(ActivityResponse.class));

        when(projects.countByWorkspace_IdAndStatus(WORKSPACE_ID, ProjectStatus.ACTIVE)).thenReturn(2L);
        when(projects.countByWorkspace_IdAndStatus(WORKSPACE_ID, ProjectStatus.ARCHIVED)).thenReturn(1L);
        when(tasks.countForWorkspace(WORKSPACE_ID)).thenReturn(8L);
        when(tasks.countForWorkspaceByStatus(WORKSPACE_ID, TaskStatus.TODO)).thenReturn(3L);
        when(tasks.countForWorkspaceByStatus(WORKSPACE_ID, TaskStatus.IN_PROGRESS)).thenReturn(2L);
        when(tasks.countForWorkspaceByStatus(WORKSPACE_ID, TaskStatus.DONE)).thenReturn(3L);
        when(tasks.countOverdueForWorkspace(WORKSPACE_ID, LocalDate.of(2026, 8, 25))).thenReturn(2L);
        when(documents.countByWorkspace_IdAndStatus(WORKSPACE_ID, DocumentStatus.ACTIVE)).thenReturn(4L);
        when(documents.countByWorkspace_IdAndStatus(WORKSPACE_ID, DocumentStatus.ARCHIVED)).thenReturn(1L);
        when(activities.feed(WORKSPACE_ID, USER_ID, ActivityLogService.DEFAULT_LIMIT)).thenReturn(recent);

        WorkspaceDashboardResponse result = new DashboardService(
                projects, tasks, documents, access, activities, CLOCK).dashboard(WORKSPACE_ID, USER_ID);

        assertEquals(new WorkspaceDashboardResponse.Projects(2, 1), result.projects());
        assertEquals(new WorkspaceDashboardResponse.Tasks(8, 3, 2, 3, 2), result.tasks());
        assertEquals(new WorkspaceDashboardResponse.Documents(4, 1), result.documents());
        assertSame(recent, result.recentActivities());
        verify(tasks).countOverdueForWorkspace(WORKSPACE_ID, LocalDate.of(2026, 8, 25));
        verify(activities).feed(WORKSPACE_ID, USER_ID, 20);
    }

    @Test
    void nonMemberCannotReadDashboardOrRunAggregateQueries() {
        ProjectRepository projects = mock(ProjectRepository.class);
        TaskRepository tasks = mock(TaskRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        WorkspaceAccessService access = mock(WorkspaceAccessService.class);
        ActivityLogService activities = mock(ActivityLogService.class);
        doThrow(new WorkspaceNotFoundException()).when(access).requireMembership(WORKSPACE_ID, USER_ID);

        DashboardService service = new DashboardService(
                projects, tasks, documents, access, activities, CLOCK);

        assertThrows(WorkspaceNotFoundException.class, () -> service.dashboard(WORKSPACE_ID, USER_ID));
        verifyNoInteractions(projects, tasks, documents, activities);
    }
}
