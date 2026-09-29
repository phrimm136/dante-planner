package org.danteplanner.backend.planner.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EncodedGiftIdTest {

    @ParameterizedTest
    @CsvSource({"9001, 0, 9001", "19001, 1, 9001", "29001, 2, 9001", "39001, 0, 39001", "gift_a, 0, gift_a",
            "'', 0, ''"})
    void parse_WhenGivenAStoredGiftId_SplitsTheEnhancementFromTheBase(String encoded, int enhancement, String base) {
        assertThat(EncodedGiftId.parse(encoded)).isEqualTo(new EncodedGiftId(enhancement, new GiftId(base)));
    }
}
