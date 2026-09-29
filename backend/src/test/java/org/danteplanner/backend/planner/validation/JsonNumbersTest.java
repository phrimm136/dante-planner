package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.planner.validation.ContentFixtures.json;

class JsonNumbersTest {

    @ParameterizedTest
    @CsvSource({"1, 1", "1.0, 1", "1e2, 100", "-0.0, 0", "2147483647, 2147483647", "-2147483648, -2147483648"})
    void intValue_WhenTheNumberIsExactlyIntegralWithinAnInt_ReturnsIt(String number, int expected) {
        assertThat(JsonNumbers.intValue(json(number))).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "2147483648", "99999999999", "1e400", "\"1\"", "null", "true", "[]", "{}"})
    void intValue_WhenTheValueIsNotAnIntegralInt_ReturnsEmpty(String value) {
        assertThat(JsonNumbers.intValue(json(value))).isEmpty();
    }

    @Test
    void intValue_WhenABigDecimalHasTrailingZeros_ReturnsItsInteger() throws Exception {
        ObjectMapper decimals = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

        assertThat(JsonNumbers.intValue(decimals.readTree("3.00"))).contains(3);
        assertThat(JsonNumbers.intValue(decimals.readTree("3.50"))).isEmpty();
    }
}
