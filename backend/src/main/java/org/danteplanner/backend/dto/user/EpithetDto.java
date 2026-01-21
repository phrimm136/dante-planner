package org.danteplanner.backend.dto.user;

/**
 * DTO representing a username epithet.
 * Used to display available epithet options for username customization.
 * The frontend maps keyword to display name via i18n.
 *
 * @param keyword Internal identifier for the epithet (e.g., "NAIVE")
 */
public record EpithetDto(
    String keyword
) {}
