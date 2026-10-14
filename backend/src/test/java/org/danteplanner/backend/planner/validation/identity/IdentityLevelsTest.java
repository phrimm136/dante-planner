package org.danteplanner.backend.planner.validation.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityLevelsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CAP = 60;

    @Test
    void clip_WhenALevelIsAboveTheCap_ReplacesItWithTheCapInACopy() throws JsonProcessingException {
        JsonNode content = equipment("{\"2\":{\"identity\":{\"id\":\"10201\",\"level\":61,\"uptie\":4}},"
                + "\"5\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4}}}");

        JsonNode clipped = IdentityLevels.clip(content, CAP);

        assertThat(clipped.toString()).isEqualTo("{\"equipment\":{"
                + "\"2\":{\"identity\":{\"id\":\"10201\",\"level\":60,\"uptie\":4}},"
                + "\"5\":{\"identity\":{\"id\":\"10501\",\"level\":45,\"uptie\":4}}}}");
        assertThat(content.path("equipment").path("2").path("identity").path("level").asInt()).isEqualTo(61);
    }

    @Test
    void clip_WhenTheCapIsTheMd6Cap_ClipsSixtyToFiftyFive() throws JsonProcessingException {
        JsonNode content = equipment("{\"1\":{\"identity\":{\"id\":\"10101\",\"level\":60,\"uptie\":4}}}");

        assertThat(IdentityLevels.clip(content, 55).path("equipment").path("1").path("identity").path("level"))
                .isEqualTo(MAPPER.readTree("55"));
    }

    @Test
    void clip_WhenALevelExceedsAnInt_ClipsItToTheCap() throws JsonProcessingException {
        JsonNode content = equipment("{\"1\":{\"identity\":{\"id\":\"10101\",\"level\":3000000000,\"uptie\":4}}}");

        assertThat(IdentityLevels.clip(content, CAP).path("equipment").path("1").path("identity").path("level"))
                .isEqualTo(MAPPER.readTree("60"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"61.0", "6.1e1", "3000000000.0"})
    void clip_WhenAnIntegralDoubleIsAboveTheCap_WritesTheCapAsAnInteger(String level) throws JsonProcessingException {
        JsonNode content = equipment("{\"1\":{\"identity\":{\"id\":\"10101\",\"level\":" + level + ",\"uptie\":4}}}");

        JsonNode clipped = IdentityLevels.clip(content, CAP).path("equipment").path("1").path("identity").path("level");

        assertThat(clipped.isInt()).isTrue();
        assertThat(clipped.intValue()).isEqualTo(CAP);
    }

    @ParameterizedTest
    @CsvSource({"60.0,60", "1.0,1", "0.0,0", "-1.0,-1", "-3000000000.0,-3000000000"})
    void clip_WhenAnIntegralDoubleIsWithinTheCap_WritesItAsThatInteger(String level, long expected)
            throws JsonProcessingException {
        JsonNode content = equipment("{\"1\":{\"identity\":{\"id\":\"10101\",\"level\":" + level + ",\"uptie\":4}}}");

        JsonNode written = IdentityLevels.clip(content, CAP).path("equipment").path("1").path("identity").path("level");

        assertThat(written.isIntegralNumber()).isTrue();
        assertThat(written.longValue()).isEqualTo(expected);
    }

    @Test
    void clip_WhenOnlyUptieIsAnIntegralDouble_LeavesItUntouched() throws JsonProcessingException {
        JsonNode content = equipment("{\"1\":{\"identity\":{\"id\":\"10101\",\"level\":45,\"uptie\":4.0}}}");

        assertThat(IdentityLevels.clip(content, CAP)).isSameAs(content);
    }

    @Test
    void clip_WhenClippedTwice_ReturnsTheFirstResultUnchanged() throws JsonProcessingException {
        JsonNode once = IdentityLevels.clip(
                equipment("{\"1\":{\"identity\":{\"id\":\"10101\",\"level\":61,\"uptie\":4}}}"), CAP);

        assertThat(IdentityLevels.clip(once, CAP)).isSameAs(once);
    }

    @ParameterizedTest
    @ValueSource(strings = {"60", "1", "0", "-1", "-3000000000", "\"60\"", "\"99\"", "1.5", "61.5", "1e400", "null",
            "true", "[]", "{}"})
    void clip_WhenTheLevelIsAnIntegerWithinTheCapOrNotIntegral_ReturnsTheContentItself(String level)
            throws JsonProcessingException {
        JsonNode content = equipment("{\"1\":{\"identity\":{\"id\":\"10101\",\"level\":" + level + ",\"uptie\":4}}}");

        assertThat(IdentityLevels.clip(content, CAP)).isSameAs(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"equipment\":{\"1\":{\"identity\":{\"id\":\"10101\",\"uptie\":4}}}}",
            "{\"equipment\":{\"1\":{\"egos\":{}}}}",
            "{\"equipment\":{\"1\":{\"identity\":5}}}",
            "{\"equipment\":[{\"identity\":{\"level\":99}}]}",
            "{\"equipment\":5}",
            "{}",
            "[]"})
    void clip_WhenThereIsNoIdentityLevelToClip_ReturnsTheContentItself(String raw) throws JsonProcessingException {
        JsonNode content = MAPPER.readTree(raw);

        assertThat(IdentityLevels.clip(content, CAP)).isSameAs(content);
    }

    private static JsonNode equipment(String equipment) throws JsonProcessingException {
        return MAPPER.readTree("{\"equipment\":" + equipment + "}");
    }
}
