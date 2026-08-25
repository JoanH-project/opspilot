package com.opspilot.dashboard.dto;

import java.util.List;

import com.opspilot.activity.dto.ActivityResponse;

public record WorkspaceDashboardResponse(
        Long workspaceId,
        Projects projects,
        Tasks tasks,
        Documents documents,
        List<ActivityResponse> recentActivities) {

    public record Projects(long active, long archived) {
    }

    public record Tasks(long total, long todo, long inProgress, long done, long overdue) {
    }

    public record Documents(long active, long archived) {
    }
}
