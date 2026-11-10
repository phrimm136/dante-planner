package org.danteplanner.backend.shared.freshness;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoDispatchStampTest {

    @Test
    void around_WhenCalled_RunsTheDispatchWithoutOpeningAWindow() {
        AtomicBoolean windowOpenInside = new AtomicBoolean(true);

        new NoDispatchStamp().around(() -> windowOpenInside.set(FreshnessDeclarations.isWindowOpen()));

        assertThat(windowOpenInside).isFalse();
    }
}
