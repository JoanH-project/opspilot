package com.opspilot.project;

import java.util.List;
import com.opspilot.project.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProjectController {
    private final ProjectService service;
    public ProjectController(ProjectService service){this.service=service;}
    @PostMapping("/api/workspaces/{workspaceId}/projects")
    public ResponseEntity<ProjectResponse> create(Authentication a,@PathVariable Long workspaceId,@Valid @RequestBody CreateProjectRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workspaceId,userId(a),r));}
    @GetMapping("/api/workspaces/{workspaceId}/projects")
    public List<ProjectResponse> list(Authentication a,@PathVariable Long workspaceId,@RequestParam(defaultValue="ACTIVE") ProjectStatus status){return service.list(workspaceId,userId(a),status);}
    @GetMapping("/api/projects/{projectId}")
    public ProjectResponse get(Authentication a,@PathVariable Long projectId){return service.get(projectId,userId(a));}
    @PatchMapping("/api/projects/{projectId}")
    public ProjectResponse update(Authentication a,@PathVariable Long projectId,@Valid @RequestBody UpdateProjectRequest r){return service.update(projectId,userId(a),r);}
    @PostMapping("/api/projects/{projectId}/archive")
    public ProjectResponse archive(Authentication a,@PathVariable Long projectId){return service.archive(projectId,userId(a));}
    private Long userId(Authentication a){return (Long)a.getPrincipal();}
}
