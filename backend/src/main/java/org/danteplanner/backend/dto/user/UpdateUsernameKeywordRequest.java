package org.danteplanner.backend.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for updating a user's username association keyword.
 *
 * @param keyword The association keyword to use for username generation (e.g., "W_CORP")
 */
public record UpdateUsernameKeywordRequest(
    @NotBlank(message = "Keyword is required")
    String keyword
) {}
