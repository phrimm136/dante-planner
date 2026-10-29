package org.danteplanner.backend.architecture.fixture;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JacksonAnnotatedRecord(String value) {
}
