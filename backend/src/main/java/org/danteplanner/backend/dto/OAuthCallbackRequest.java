package org.danteplanner.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthCallbackRequest {

    @NotBlank(message = "Authorization code is required")
    private String code;

    @NotBlank(message = "Provider is required")
    private String provider;

    @NotBlank(message = "Code verifier is required for PKCE")
    private String codeVerifier;
}
