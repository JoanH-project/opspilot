package com.opspilot.activity;

import java.util.List;

import com.opspilot.activity.dto.ActivityResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/activities")
public class ActivityController {
    private final ActivityLogService activityLogService;

    public ActivityController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    public List<ActivityResponse> feed(Authentication authentication, @PathVariable Long workspaceId,
                                       @RequestParam(defaultValue = "20") int limit) {
        return activityLogService.feed(workspaceId, (Long) authentication.getPrincipal(), limit);
    }
}
