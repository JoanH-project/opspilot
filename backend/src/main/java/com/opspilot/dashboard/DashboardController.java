package com.opspilot.dashboard;

import com.opspilot.dashboard.dto.WorkspaceDashboardResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public WorkspaceDashboardResponse get(Authentication authentication, @PathVariable Long workspaceId) {
        return dashboardService.dashboard(workspaceId, (Long) authentication.getPrincipal());
    }
}
