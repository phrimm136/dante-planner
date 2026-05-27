package org.danteplanner.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineageRotationFlagTest {

    @Test
    void isEnabled_WhenSeededTrue_ReturnsTrue() {
        LineageRotationFlag flag = new LineageRotationFlag(true);

        assertThat(flag.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_WhenSeededFalse_ReturnsFalse() {
        LineageRotationFlag flag = new LineageRotationFlag(false);

        assertThat(flag.isEnabled()).isFalse();
    }

    @Test
    void setEnabled_FlipsValue() {
        LineageRotationFlag flag = new LineageRotationFlag(false);

        flag.setEnabled(true);
        assertThat(flag.isEnabled()).isTrue();

        flag.setEnabled(false);
        assertThat(flag.isEnabled()).isFalse();
    }
}
