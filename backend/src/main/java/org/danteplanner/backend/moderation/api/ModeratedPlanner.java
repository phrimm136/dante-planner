package org.danteplanner.backend.moderation.api;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record ModeratedPlanner(
    UUID id,
    String title,
    boolean published,
    boolean hiddenFromRecommended,
    @Nullable Long hiddenByModeratorId,
    @Nullable String hiddenReason,
    @Nullable Instant hiddenAt
) {}
