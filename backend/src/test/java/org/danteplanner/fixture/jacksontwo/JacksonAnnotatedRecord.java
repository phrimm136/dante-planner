package org.danteplanner.fixture.jacksontwo;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JacksonAnnotatedRecord(String value) {
}
