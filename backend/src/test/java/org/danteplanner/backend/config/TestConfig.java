package org.danteplanner.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration class providing beans needed for integration tests.
 *
 * <p>This configuration provides a Jackson 2.x ObjectMapper since the application
 * code uses com.fasterxml.jackson while Spring Boot 4.0.0-SNAPSHOT auto-configures
 * Jackson 3.x (tools.jackson).</p>
 */
@TestConfiguration
public class TestConfig {

    /**
     * Provides a Jackson 2.x ObjectMapper for use in tests.
     *
     * @return configured ObjectMapper instance
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
