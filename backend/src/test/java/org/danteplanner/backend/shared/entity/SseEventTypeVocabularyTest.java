package org.danteplanner.backend.shared.entity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventTypeVocabularyTest {

    private static final Path VOCABULARY = Path.of("../testdata/sse-event-types.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INDENT = "  ";

    @Test
    void vocabularyFile_WhenComparedWithTheEnum_HoldsEveryWireValueSorted() throws IOException {
        String expected = Arrays.stream(SseEventType.values())
                .map(SseEventType::getValue)
                .sorted()
                .map(value -> INDENT + quoted(value))
                .collect(Collectors.joining(",\n", "[\n", "\n]\n"));

        assertThat(Files.readString(VOCABULARY, StandardCharsets.UTF_8))
                .withFailMessage("%s is stale; regenerate it with exactly this content:%n%s", VOCABULARY, expected)
                .isEqualTo(expected);
    }

    private static String quoted(String value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
