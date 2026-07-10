package org.danteplanner.backend.readpath;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.danteplanner.backend.shared.readpath.ByIdReadGuard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario test for the Phase-2 read-path seam — {@link ByIdReadGuard#read} is a pure pass-through:
 * it invokes the dereference supplier exactly once and returns exactly what the supplier produced.
 * No routing, re-check, or tombstone yet (Phases 3/4/8).
 */
class ByIdReadGuardTest {

    private final ByIdReadGuard guard = new ByIdReadGuard();

    @Test
    void read_WhenInvoked_ReturnsSupplierResult() {
        Object sentinel = new Object();

        Object result = guard.read("planner", UUID.randomUUID(), () -> sentinel);

        assertThat(result).isSameAs(sentinel);
    }

    @Test
    void read_WhenInvoked_InvokesSupplierExactlyOnce() {
        AtomicInteger calls = new AtomicInteger(0);

        guard.read("planner", UUID.randomUUID(), () -> {
            calls.incrementAndGet();
            return "value";
        });

        assertThat(calls.get()).isEqualTo(1);
    }
}
