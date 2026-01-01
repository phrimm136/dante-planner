package org.danteplanner.backend.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration for JSON serialization/deserialization.
 *
 * Explicitly defines ObjectMapper bean since Spring Boot 4.0 SNAPSHOT
 * may not auto-configure it with spring-boot-starter-webmvc.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 date/time module for Instant, LocalDateTime, etc.
        mapper.registerModule(new JavaTimeModule());

        // Write dates as ISO-8601 strings, not timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Don't fail on unknown properties (forward compatibility)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}
