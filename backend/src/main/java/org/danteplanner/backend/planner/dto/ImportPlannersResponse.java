package org.danteplanner.backend.planner.dto;

import lombok.Builder;
import org.danteplanner.backend.planner.exception.PlannerValidationException;

import java.util.List;

@Builder
public record ImportPlannersResponse(
    int imported,
    int total,
    List<PlannerSummaryResponse> planners,
    List<SkippedPlanner> skipped
) {

    public record SkippedPlanner(String id, String title, List<SkipReason> errors) {

        public static SkippedPlanner from(UpsertPlannerRequest request, PlannerValidationException rejection) {
            List<SkipReason> errors = rejection.getSubErrors().isEmpty()
                    ? List.of(new SkipReason(rejection.getOriginalCode()))
                    : rejection.getSubErrors().stream()
                            .map(sub -> new SkipReason(sub.code()))
                            .toList();
            return new SkippedPlanner(request.id(), request.title(), errors);
        }
    }

    public record SkipReason(String code) {}
}
