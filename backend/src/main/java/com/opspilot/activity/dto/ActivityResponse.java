package com.opspilot.activity.dto;

import java.time.Instant;

import com.opspilot.activity.ActivityLog;
import com.opspilot.activity.ActivityType;

public record ActivityResponse(
        Long id,
        ActivityType type,
        String entityType,
        Long entityId,
        String message,
        Actor actor,
        Instant createdAt) {

    public record Actor(Long id, String name) {
    }

    public static ActivityResponse from(ActivityLog activity) {
        return new ActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getEntityType(),
                activity.getEntityId(),
                activity.getMessage(),
                new Actor(activity.getActor().getId(), activity.getActor().getName()),
                activity.getCreatedAt());
    }
}
