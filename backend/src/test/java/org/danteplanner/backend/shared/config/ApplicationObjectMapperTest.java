package org.danteplanner.backend.shared.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ApplicationObjectMapperTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void problemDetail_WhenCarryingACodeProperty_RendersTheCodeAtTopLevel() throws JsonProcessingException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "bad input");
        problem.setProperty("code", "VALIDATION_ERROR");

        Map<String, Object> json = objectMapper.readValue(
                objectMapper.writeValueAsString(problem), new TypeReference<>() {});

        assertThat(json).containsEntry("code", "VALIDATION_ERROR");
        assertThat(json).doesNotContainKey("properties");
    }

    @Test
    void response_WhenAFieldIsNull_OmitsIt() throws JsonProcessingException {
        Map<String, Object> json = objectMapper.readValue(
                objectMapper.writeValueAsString(new Pair("kept", null)), new TypeReference<>() {});

        assertThat(json).containsOnlyKeys("present");
    }

    private record Pair(String present, String absent) {
    }
}
