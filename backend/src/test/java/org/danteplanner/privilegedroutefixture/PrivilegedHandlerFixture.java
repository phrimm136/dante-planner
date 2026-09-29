package org.danteplanner.privilegedroutefixture;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.moderation.service.PlannerModerationService;

@RestController
@RequestMapping("/api/moderation")
public class PrivilegedHandlerFixture {

    private final PlannerModerationService plannerModerationService;

    public PrivilegedHandlerFixture(PlannerModerationService plannerModerationService) {
        this.plannerModerationService = plannerModerationService;
    }

    @PostMapping("/fixture/{id}/unpublish")
    public String unpublish(@PathVariable UUID id) {
        plannerModerationService.unpublishPlanner(1L, id);
        return "reached";
    }

    @GetMapping
    public String root() {
        return "reached";
    }
}
