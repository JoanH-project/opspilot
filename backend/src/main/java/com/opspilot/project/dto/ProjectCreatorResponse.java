package com.opspilot.project.dto;
import com.opspilot.user.User;
public record ProjectCreatorResponse(Long id,String email,String name) { public static ProjectCreatorResponse from(User u){return new ProjectCreatorResponse(u.getId(),u.getEmail(),u.getName());} }
