package org.danteplanner.backend.shared.config;

import java.nio.charset.StandardCharsets;
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

    private static final int GRINNING_FACE = 0x1F600;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void response_WhenAFieldIsNull_OmitsIt() throws JacksonException {
        Map<String, Object> json = objectMapper.readValue(
                objectMapper.writeValueAsString(new Pair("kept", null)), new TypeReference<>() {});

        assertThat(json).containsOnlyKeys("present");
    }

    @Test
    void response_WhenFieldsAreDeclaredOutOfAlphabeticalOrder_KeepsTheDeclaredOrder() throws JacksonException {
        assertThat(objectMapper.writeValueAsString(new Declared()))
                .isEqualTo("{\"zebra\":\"z\",\"apple\":\"a\"}");
    }

    @Test
    void response_WhenAStringHoldsASurrogatePair_WritesBothHalvesEscaped() throws JacksonException {
        byte[] bytes = objectMapper.writeValueAsBytes(new String(Character.toChars(GRINNING_FACE)));

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("\"\\uD83D\\uDE00\"");
    }

    private record Pair(String present, String absent) {
    }

    static final class Declared {

        private final String zebra = "z";
        private final String apple = "a";

        public String getZebra() {
            return zebra;
        }

        public String getApple() {
            return apple;
        }
    }
}
