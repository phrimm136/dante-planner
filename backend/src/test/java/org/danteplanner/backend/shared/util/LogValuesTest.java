package org.danteplanner.backend.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogValuesTest {

    @Test
    void oneLine_WhenValueCarriesLineBreaks_ReplacesEach() {
        assertThat(LogValues.oneLine("/api/x\r\nforged\nline\r")).isEqualTo("/api/x__forged_line_");
    }

    @Test
    void oneLine_WhenValueIsSingleLine_ReturnsItUnchanged() {
        assertThat(LogValues.oneLine("Mozilla/5.0 (X11; Linux x86_64)")).isEqualTo("Mozilla/5.0 (X11; Linux x86_64)");
    }

    @Test
    void oneLine_WhenValueIsAbsent_ReturnsNull() {
        assertThat(LogValues.oneLine(null)).isNull();
    }
}
