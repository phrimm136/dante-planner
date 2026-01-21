package org.danteplanner.backend.dto.user;

import java.util.List;

/**
 * Response DTO containing all available username epithets.
 * Wraps the list for consistent API response structure.
 *
 * @param epithets List of available epithets for username generation
 */
public record EpithetListResponse(
    List<String> epithets
) {}
