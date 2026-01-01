package org.danteplanner.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client configuration.
 * Provides RestTemplate bean for HTTP calls to external services (OAuth providers, etc.).
 *
 * Note: ObjectMapper is already configured in JacksonConfig.java
 */
@Configuration
public class HttpClientConfig {

    /**
     * Default RestTemplate for HTTP calls.
     * Injected into services that need to call external APIs (e.g., OAuth providers).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
