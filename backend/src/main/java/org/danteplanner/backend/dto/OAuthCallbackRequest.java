package org.danteplanner.backend.dto;

import lombok.Data;

@Data
public class OAuthCallbackRequest {
    private String code;
    private String provider;
}
