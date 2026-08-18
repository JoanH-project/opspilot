package com.opspilot.project.dto;
import jakarta.validation.constraints.Size;
public record UpdateProjectRequest(@Size(max=150,message="Project name must not exceed 150 characters") String name,
                                   @Size(max=5000,message="Project description must not exceed 5000 characters") String description) { }
