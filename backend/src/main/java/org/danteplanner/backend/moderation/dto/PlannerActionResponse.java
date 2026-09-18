package org.danteplanner.backend.moderation.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PlannerActionResponse(
    UUID plannerId,
    String message
) {}
