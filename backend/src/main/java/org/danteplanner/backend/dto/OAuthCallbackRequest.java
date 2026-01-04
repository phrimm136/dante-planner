package org.danteplanner.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for OAuth callback handling.
 *
 * <p>Includes size validation on code and codeVerifier to prevent
 * memory exhaustion attacks with oversized payloads.</p>
 */
@Data
public class OAuthCallbackRequest {

    @NotBlank(message = "Authorization code is required")
    @Size(max = 512, message = "Authorization code exceeds maximum length")
    private String code;

    @NotBlank(message = "Provider is required")
    @Size(max = 32, message = "Provider exceeds maximum length")
    private String provider;

    @NotBlank(message = "Code verifier is required for PKCE")
    @Size(max = 128, message = "Code verifier exceeds maximum length")
    private String codeVerifier;
}
