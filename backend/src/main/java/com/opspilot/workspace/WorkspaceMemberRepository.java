package com.opspilot.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {
    Optional<WorkspaceMember> findByWorkspace_IdAndUser_Id(Long workspaceId, Long userId);
    List<WorkspaceMember> findByUser_IdOrderByWorkspace_CreatedAtDesc(Long userId);
    List<WorkspaceMember> findByWorkspace_IdOrderByJoinedAtAsc(Long workspaceId);
}
