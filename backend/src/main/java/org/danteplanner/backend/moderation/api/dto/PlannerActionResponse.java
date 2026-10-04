package org.danteplanner.backend.moderation.api.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PlannerActionResponse(
    UUID plannerId,
    String message
) {}
