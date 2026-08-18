package com.opspilot.workspace;

import org.springframework.stereotype.Service;

@Service
public class WorkspaceAccessService {
    private final WorkspaceMemberRepository memberRepository;
    public WorkspaceAccessService(WorkspaceMemberRepository memberRepository) { this.memberRepository = memberRepository; }
    public WorkspaceMember requireMembership(Long workspaceId, Long userId) {
        return memberRepository.findByWorkspace_IdAndUser_Id(workspaceId, userId).orElseThrow(WorkspaceNotFoundException::new);
    }
    public WorkspaceMember requireAdminOrOwner(Long workspaceId, Long userId) {
        WorkspaceMember member = requireMembership(workspaceId, userId);
        if (member.getRole() != WorkspaceRole.OWNER && member.getRole() != WorkspaceRole.ADMIN) throw new InsufficientWorkspaceRoleException();
        return member;
    }
}
