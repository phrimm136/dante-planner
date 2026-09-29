package org.danteplanner.privilegedroutefixture;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.danteplanner.backend.moderation.service.PlannerModerationService;

@RestController
@RequestMapping("/api/fixture")
public class StrayModerationHandlerFixture {

    private final PlannerModerationService plannerModerationService;

    public StrayModerationHandlerFixture(PlannerModerationService plannerModerationService) {
        this.plannerModerationService = plannerModerationService;
    }

    @PostMapping("/planner/{id}/unpublish")
    public String unpublish(@PathVariable UUID id) {
        plannerModerationService.unpublishPlanner(1L, id);
        return "reached";
    }
}
