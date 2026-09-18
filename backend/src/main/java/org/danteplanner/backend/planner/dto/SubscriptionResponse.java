package org.danteplanner.backend.planner.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record SubscriptionResponse(
    UUID plannerId,
    boolean subscribed
) {}
