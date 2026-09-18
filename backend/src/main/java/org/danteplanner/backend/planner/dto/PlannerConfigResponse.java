package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PlannerConfigResponse(
    int schemaVersion,
    int mdCurrentVersion,
    List<Integer> mdAvailableVersions,
    List<Integer> rrAvailableVersions
) {}
