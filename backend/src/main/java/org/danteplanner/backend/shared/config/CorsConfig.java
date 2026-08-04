package org.danteplanner.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Explicit header whitelist - no wildcards for security
        // Content-Type: Required for JSON API calls
        // Cache-Control: Client cache control
        // X-CSRF-Token: Double-submit CSRF token echoed by the SPA on mutating requests
        configuration.setAllowedHeaders(List.of("Content-Type", "Cache-Control", "X-CSRF-Token"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
