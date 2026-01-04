package org.danteplanner.backend.dto.user;

import java.util.List;

/**
 * Response DTO containing all available username associations.
 * Wraps the list for consistent API response structure.
 *
 * @param associations List of available associations for username generation
 */
public record AssociationListResponse(
    List<AssociationDto> associations
) {}
