package com.opspilot.task;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import com.opspilot.activity.ActivityLogService;
import com.opspilot.activity.ActivityType;
import com.opspilot.project.*;
import com.opspilot.task.dto.*;
import com.opspilot.user.*;
import com.opspilot.workspace.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final WorkspaceAccessService access;
    private final ActivityLogService activityLogService;

    public TaskService(TaskRepository tasks, ProjectRepository projects, UserRepository users,
                       WorkspaceAccessService access, ActivityLogService activityLogService) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
        this.access = access;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public TaskResponse create(Long projectId, Long userId, CreateTaskRequest request) {
        Project project = project(projectId, userId);
        editable(project);
        User creator = users.findById(userId).orElseThrow(TaskNotFoundException::new);
        User assignee = assignee(project, request.assigneeId());
        Task saved = tasks.saveAndFlush(new Task(project, request.title().trim(), normalize(request.description()),
                request.priority() == null ? TaskPriority.MEDIUM : request.priority(),
                assignee, creator, request.dueDate()));
        activityLogService.record(project.getWorkspace(), creator, ActivityType.TASK_CREATED, "TASK",
                saved.getId(), creator.getName() + " created task " + saved.getTitle());
        return TaskResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long projectId, Long userId, TaskStatus status,
                                   TaskPriority priority, Long assigneeId) {
        Project project = project(projectId, userId);
        return tasks.findByProject_IdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(task -> status == null || task.getStatus() == status)
                .filter(task -> priority == null || task.getPriority() == priority)
                .filter(task -> assigneeId == null
                        || (task.getAssignee() != null && task.getAssignee().getId().equals(assigneeId)))
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id, Long userId) {
        return TaskResponse.from(task(id, userId));
    }

    @Transactional
    public TaskResponse update(Long id, Long userId, UpdateTaskRequest request) {
        Task task = task(id, userId);
        editable(task.getProject());
        if (request.title() != null && request.title().isBlank()) {
            throw new InvalidTaskUpdateException("Task title must not be blank");
        }

        String title = request.title() == null ? task.getTitle() : request.title().trim();
        String description = request.description() == null ? task.getDescription() : normalize(request.description());
        TaskPriority priority = request.priority() == null ? task.getPriority() : request.priority();
        User updatedAssignee = request.clearAssignee() ? null
                : request.assigneeId() == null ? task.getAssignee() : assignee(task.getProject(), request.assigneeId());
        LocalDate dueDate = request.clearDueDate() ? null
                : request.dueDate() == null ? task.getDueDate() : request.dueDate();

        boolean changed = !Objects.equals(title, task.getTitle())
                || !Objects.equals(description, task.getDescription())
                || priority != task.getPriority()
                || !sameUser(updatedAssignee, task.getAssignee())
                || !Objects.equals(dueDate, task.getDueDate());
        if (!changed) {
            return TaskResponse.from(task);
        }

        task.update(request.title() == null ? null : title,
                request.description() == null ? null : description,
                request.priority(),
                request.assigneeId() == null ? null : updatedAssignee,
                request.dueDate(),
                request.clearAssignee(),
                request.clearDueDate());
        Task saved = tasks.saveAndFlush(task);
        User actor = users.findById(userId).orElseThrow(TaskNotFoundException::new);
        activityLogService.record(saved.getProject().getWorkspace(), actor, ActivityType.TASK_UPDATED, "TASK",
                saved.getId(), actor.getName() + " updated task " + saved.getTitle());
        return TaskResponse.from(saved);
    }

    @Transactional
    public TaskResponse status(Long id, Long userId, UpdateTaskStatusRequest request) {
        Task task = task(id, userId);
        editable(task.getProject());
        TaskStatus oldStatus = task.getStatus();
        if (oldStatus == request.status()) {
            return TaskResponse.from(task);
        }

        task.status(request.status());
        Task saved = tasks.saveAndFlush(task);
        User actor = users.findById(userId).orElseThrow(TaskNotFoundException::new);
        activityLogService.record(saved.getProject().getWorkspace(), actor, ActivityType.TASK_STATUS_CHANGED, "TASK",
                saved.getId(), actor.getName() + " changed task " + saved.getTitle()
                        + " status from " + oldStatus + " to " + saved.getStatus());
        return TaskResponse.from(saved);
    }

    private Task task(Long id, Long userId) {
        Task task = tasks.findById(id).orElseThrow(TaskNotFoundException::new);
        access.requireMembership(task.getProject().getWorkspace().getId(), userId);
        return task;
    }

    private Project project(Long id, Long userId) {
        Project project = projects.findById(id).orElseThrow(ProjectNotFoundException::new);
        access.requireMembership(project.getWorkspace().getId(), userId);
        return project;
    }

    private void editable(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ArchivedProjectException();
        }
    }

    private User assignee(Project project, Long id) {
        if (id == null) {
            return null;
        }
        User user = users.findById(id).orElseThrow(InvalidTaskAssigneeException::new);
        try {
            access.requireMembership(project.getWorkspace().getId(), user.getId());
            return user;
        } catch (WorkspaceNotFoundException exception) {
            throw new InvalidTaskAssigneeException();
        }
    }

    private boolean sameUser(User left, User right) {
        return left == null ? right == null : right != null && left.getId().equals(right.getId());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
