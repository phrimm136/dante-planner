package org.danteplanner.backend.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP client configuration.
 * Provides RestTemplate bean for HTTP calls to external services (OAuth providers, etc.).
 *
 * Note: ObjectMapper is already configured in JacksonConfig.java
 */
@Configuration
public class HttpClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    /**
     * Default RestTemplate for HTTP calls.
     * Injected into services that need to call external APIs (e.g., OAuth providers).
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
