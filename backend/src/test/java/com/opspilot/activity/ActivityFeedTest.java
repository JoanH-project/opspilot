package com.opspilot.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import com.opspilot.common.error.GlobalExceptionHandler;
import com.opspilot.user.User;
import com.opspilot.workspace.Workspace;
import com.opspilot.workspace.WorkspaceAccessService;
import com.opspilot.workspace.WorkspaceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActivityFeedTest {
    @Test
    void memberReceivesOnlyRequestedWorkspaceActivitiesNewestFirstWithCustomLimit() throws Exception {
        ActivityLogRepository repository = mock(ActivityLogRepository.class);
        WorkspaceAccessService access = mock(WorkspaceAccessService.class);
        ActivityLogService service = new ActivityLogService(repository, access);
        User actor = user(7L, "Owner");
        Workspace workspace = workspace(1L, actor);
        ActivityLog newest = activity(2L, workspace, actor, ActivityType.TASK_UPDATED,
                "updated", Instant.parse("2026-08-25T02:00:00Z"));
        ActivityLog older = activity(1L, workspace, actor, ActivityType.PROJECT_CREATED,
                "created", Instant.parse("2026-08-25T01:00:00Z"));
        when(repository.findByWorkspace_IdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(newest, older));

        var response = service.feed(1L, 7L, 10);

        assertEquals(List.of(2L, 1L), response.stream().map(item -> item.id()).toList());
        assertEquals("Owner", response.getFirst().actor().name());
        verify(access).requireMembership(1L, 7L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByWorkspace_IdOrderByCreatedAtDesc(eq(1L), pageable.capture());
        assertEquals(10, pageable.getValue().getPageSize());
    }

    @Test
    void nonMemberGetsNotFoundAndRepositoryIsNotQueried() {
        ActivityLogRepository repository = mock(ActivityLogRepository.class);
        WorkspaceAccessService access = mock(WorkspaceAccessService.class);
        doThrow(new WorkspaceNotFoundException()).when(access).requireMembership(1L, 9L);
        ActivityLogService service = new ActivityLogService(repository, access);

        assertThrows(WorkspaceNotFoundException.class, () -> service.feed(1L, 9L, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void controllerUsesDefaultLimitAndReturnsSafeActorShape() throws Exception {
        ActivityLogRepository repository = mock(ActivityLogRepository.class);
        WorkspaceAccessService access = mock(WorkspaceAccessService.class);
        User actor = user(7L, "Owner");
        Workspace workspace = workspace(1L, actor);
        when(repository.findByWorkspace_IdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of(activity(1L, workspace, actor, ActivityType.WORKSPACE_CREATED,
                        "Owner created workspace Team", Instant.parse("2026-08-25T01:00:00Z"))));
        MockMvc mockMvc = mockMvc(new ActivityLogService(repository, access));

        mockMvc.perform(get("/api/workspaces/1/activities")
                        .principal(new UsernamePasswordAuthenticationToken(7L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor.id").value(7))
                .andExpect(jsonPath("$[0].actor.name").value("Owner"))
                .andExpect(jsonPath("$[0].actor.passwordHash").doesNotExist());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByWorkspace_IdOrderByCreatedAtDesc(eq(1L), pageable.capture());
        assertEquals(ActivityLogService.DEFAULT_LIMIT, pageable.getValue().getPageSize());
    }

    @Test
    void invalidLimitsReturnStructuredBadRequest() throws Exception {
        MockMvc mockMvc = mockMvc(new ActivityLogService(
                mock(ActivityLogRepository.class), mock(WorkspaceAccessService.class)));
        var principal = new UsernamePasswordAuthenticationToken(7L, null);

        mockMvc.perform(get("/api/workspaces/1/activities?limit=0").principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Activity limit must be between 1 and 100"));
        mockMvc.perform(get("/api/workspaces/1/activities?limit=101").principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void recordRequiresAnExistingTransaction() throws Exception {
        var transactional = ActivityLogService.class
                .getMethod("record", Workspace.class, User.class, ActivityType.class,
                        String.class, Long.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY,
                transactional.propagation());
    }

    private MockMvc mockMvc(ActivityLogService service) {
        return MockMvcBuilders.standaloneSetup(new ActivityController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static User user(Long id, String name) throws Exception {
        User user = new User("owner@example.com", "secret-hash", name);
        set(user, "id", id);
        return user;
    }

    private static Workspace workspace(Long id, User owner) throws Exception {
        Workspace workspace = new Workspace("Team", owner);
        set(workspace, "id", id);
        return workspace;
    }

    private static ActivityLog activity(Long id, Workspace workspace, User actor, ActivityType type,
                                        String message, Instant createdAt) throws Exception {
        ActivityLog activity = new ActivityLog(workspace, actor, type, "TEST", id, message);
        set(activity, "id", id);
        set(activity, "createdAt", createdAt);
        return activity;
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
