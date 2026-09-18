package org.danteplanner.backend.moderation.dto;

import java.util.UUID;

public record UnpublishPlannerResponse(UUID plannerId, boolean published, String message) {
}
