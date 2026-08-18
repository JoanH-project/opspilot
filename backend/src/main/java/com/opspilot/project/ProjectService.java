package com.opspilot.project;

import java.util.List;
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
    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository, WorkspaceAccessService workspaceAccess) {
        this.projectRepository=projectRepository; this.userRepository=userRepository; this.workspaceAccess=workspaceAccess;
    }
    @Transactional
    public ProjectResponse create(Long workspaceId, Long userId, CreateProjectRequest request) {
        WorkspaceMember membership=workspaceAccess.requireMembership(workspaceId,userId);
        User creator=userRepository.findById(userId).orElseThrow(ProjectNotFoundException::new);
        Project project=new Project(membership.getWorkspace(),request.name().trim(),normalizeDescription(request.description()),creator);
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }
    @Transactional(readOnly=true)
    public List<ProjectResponse> list(Long workspaceId, Long userId, ProjectStatus status) {
        workspaceAccess.requireMembership(workspaceId,userId);
        return projectRepository.findByWorkspace_IdAndStatusOrderByCreatedAtDesc(workspaceId,status).stream().map(ProjectResponse::from).toList();
    }
    @Transactional(readOnly=true)
    public ProjectResponse get(Long projectId, Long userId) { return ProjectResponse.from(requireAccessible(projectId,userId)); }
    @Transactional
    public ProjectResponse update(Long projectId, Long userId, UpdateProjectRequest request) {
        Project project=requireAccessible(projectId,userId);
        requireCanModify(project,userId);
        if(project.getStatus()==ProjectStatus.ARCHIVED) throw new ArchivedProjectException();
        if(request.name()!=null && request.name().isBlank()) throw new InvalidProjectUpdateException("Project name must not be blank");
        return ProjectResponse.from(projectRepository.saveAndFlush(projectUpdate(project,request)));
    }
    @Transactional
    public ProjectResponse archive(Long projectId, Long userId) {
        Project project=requireAccessible(projectId,userId);
        requireCanModify(project,userId);
        if(project.getStatus()==ProjectStatus.ARCHIVED) return ProjectResponse.from(project);
        project.archive();
        return ProjectResponse.from(projectRepository.saveAndFlush(project));
    }
    private Project projectUpdate(Project project, UpdateProjectRequest request) { project.update(request.name()==null?null:request.name().trim(),normalizeDescription(request.description())); return project; }
    private Project requireAccessible(Long projectId,Long userId) {
        Project project=projectRepository.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        workspaceAccess.requireMembership(project.getWorkspace().getId(),userId);
        return project;
    }
    private void requireCanModify(Project project,Long userId) {
        WorkspaceMember member=workspaceAccess.requireMembership(project.getWorkspace().getId(),userId);
        if(member.getRole()==WorkspaceRole.OWNER||member.getRole()==WorkspaceRole.ADMIN||project.getCreatedBy().getId().equals(userId)) return;
        throw new ProjectAccessDeniedException();
    }
    private String normalizeDescription(String description) { return description==null?null:description.trim(); }
}
