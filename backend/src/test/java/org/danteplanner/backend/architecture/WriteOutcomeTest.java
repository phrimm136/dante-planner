package org.danteplanner.backend.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.danteplanner.backend.shared.failure.WriteOutcome;

import static org.assertj.core.api.Assertions.assertThat;

class WriteOutcomeTest {

    @Test
    void of_WhenNoRowMatched_IsNothing() {
        assertThat(WriteOutcome.of(0)).isEqualTo(WriteOutcome.NOTHING);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void of_WhenRowsMatched_IsApplied(int matchedRows) {
        assertThat(WriteOutcome.of(matchedRows)).isEqualTo(WriteOutcome.APPLIED);
    }
}
