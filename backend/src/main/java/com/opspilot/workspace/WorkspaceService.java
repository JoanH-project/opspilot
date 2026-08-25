package com.opspilot.workspace;

import java.util.List;

import com.opspilot.activity.ActivityLogService;
import com.opspilot.activity.ActivityType;
import com.opspilot.user.User;
import com.opspilot.user.UserRepository;
import com.opspilot.workspace.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccess;
    private final ActivityLogService activityLogService;

    public WorkspaceService(WorkspaceRepository workspaceRepository, WorkspaceMemberRepository memberRepository,
                            UserRepository userRepository, WorkspaceAccessService workspaceAccess,
                            ActivityLogService activityLogService) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.workspaceAccess = workspaceAccess;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public WorkspaceResponse createWorkspace(Long currentUserId, CreateWorkspaceRequest request) {
        User owner = userRepository.findById(currentUserId).orElseThrow(WorkspaceNotFoundException::new);
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace(request.name().trim(), owner));
        WorkspaceMember ownerMembership = memberRepository.saveAndFlush(
                new WorkspaceMember(workspace, owner, WorkspaceRole.OWNER));
        activityLogService.record(workspace, owner, ActivityType.WORKSPACE_CREATED, "WORKSPACE",
                workspace.getId(), owner.getName() + " created workspace " + workspace.getName());
        return WorkspaceResponse.from(workspace, ownerMembership.getRole());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceSummaryResponse> listWorkspaces(Long currentUserId) {
        return memberRepository.findByUser_IdOrderByWorkspace_CreatedAtDesc(currentUserId).stream()
                .map(WorkspaceSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getWorkspace(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = workspaceAccess.requireMembership(workspaceId, currentUserId);
        return WorkspaceResponse.from(membership.getWorkspace(), membership.getRole());
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(Long workspaceId, Long currentUserId, UpdateWorkspaceRequest request) {
        WorkspaceMember membership = workspaceAccess.requireAdminOrOwner(workspaceId, currentUserId);
        Workspace workspace = membership.getWorkspace();
        workspace.rename(request.name().trim());
        Workspace savedWorkspace = workspaceRepository.saveAndFlush(workspace);
        return WorkspaceResponse.from(savedWorkspace, membership.getRole());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(Long workspaceId, Long currentUserId) {
        workspaceAccess.requireMembership(workspaceId, currentUserId);
        return memberRepository.findByWorkspace_IdOrderByJoinedAtAsc(workspaceId).stream()
                .map(WorkspaceMemberResponse::from).toList();
    }
}
