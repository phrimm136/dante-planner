package org.danteplanner.backend.moderation.api;

import java.time.Instant;
import java.util.UUID;

public record ModeratedPlanner(
    UUID id,
    String title,
    boolean published,
    boolean hiddenFromRecommended,
    Long hiddenByModeratorId,
    String hiddenReason,
    Instant hiddenAt
) {}
