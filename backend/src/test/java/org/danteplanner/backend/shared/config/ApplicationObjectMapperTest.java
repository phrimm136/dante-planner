package org.danteplanner.backend.shared.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ApplicationObjectMapperTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void response_WhenAFieldIsNull_OmitsIt() throws JacksonException {
        Map<String, Object> json = objectMapper.readValue(
                objectMapper.writeValueAsString(new Pair("kept", null)), new TypeReference<>() {});

        assertThat(json).containsOnlyKeys("present");
    }

    private record Pair(String present, String absent) {
    }
}
