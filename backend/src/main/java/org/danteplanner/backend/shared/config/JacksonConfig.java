package org.danteplanner.backend.shared.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Jackson configuration for JSON serialization/deserialization.
 *
 * Explicitly defines ObjectMapper bean since Spring Boot 4.0 SNAPSHOT
 * may not auto-configure it with spring-boot-starter-webmvc.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(List<Module> modules) {
        ObjectMapper mapper = new ObjectMapper();

        // Register Java 8 date/time module for Instant, LocalDateTime, etc.
        mapper.registerModule(new JavaTimeModule());

        // A hand-built mapper sees no context-contributed Module beans on its own; without this,
        // Spring Data's PageModule (the VIA_DTO page envelope) silently never applies.
        mapper.registerModules(modules);

        // Write dates as ISO-8601 strings, not timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Don't fail on unknown properties (forward compatibility)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // A null-valued field is absent from the payload rather than emitted as null. One policy for
        // every response, so a client reads one shape and never has to distinguish the two.
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return mapper;
    }
}
