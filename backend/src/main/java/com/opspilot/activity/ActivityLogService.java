package com.opspilot.activity;

import java.util.List;

import com.opspilot.activity.dto.ActivityResponse;
import com.opspilot.user.User;
import com.opspilot.workspace.Workspace;
import com.opspilot.workspace.WorkspaceAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityLogService {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private final ActivityLogRepository repository;
    private final WorkspaceAccessService workspaceAccess;

    public ActivityLogService(ActivityLogRepository repository, WorkspaceAccessService workspaceAccess) {
        this.repository = repository;
        this.workspaceAccess = workspaceAccess;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Workspace workspace, User actor, ActivityType type,
                       String entityType, Long entityId, String message) {
        repository.save(new ActivityLog(workspace, actor, type, entityType, entityId, message));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> feed(Long workspaceId, Long userId, int limit) {
        workspaceAccess.requireMembership(workspaceId, userId);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidActivityLimitException();
        }
        return repository.findByWorkspace_IdOrderByCreatedAtDesc(
                        workspaceId, PageRequest.of(0, limit)).stream()
                .map(ActivityResponse::from)
                .toList();
    }
}
