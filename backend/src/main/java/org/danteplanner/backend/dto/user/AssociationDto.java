package org.danteplanner.backend.dto.user;

/**
 * DTO representing a username association (keyword).
 * Used to display available association options for username customization.
 *
 * @param keyword     Internal identifier for the association (e.g., "W_CORP")
 * @param displayName Human-readable name for display (e.g., "WCorp")
 */
public record AssociationDto(
    String keyword,
    String displayName
) {}
