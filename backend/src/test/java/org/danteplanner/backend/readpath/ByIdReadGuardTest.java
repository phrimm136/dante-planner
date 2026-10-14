package org.danteplanner.backend.readpath;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;
import org.danteplanner.backend.support.RecordingTombstoneStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Without a {@code PrimaryReCheck}, {@link ByIdReadGuard#read} is a pure pass-through: it invokes
 * the dereference supplier exactly once and returns exactly what the supplier produced.
 */
class ByIdReadGuardTest {

    private final ByIdReadGuard guard = new ByIdReadGuard();

    private final RecordingTombstoneStore tombstoneStore = new RecordingTombstoneStore();

    private final ByIdReadGuard wiredGuard = new ByIdReadGuard(Optional.of(new PrimaryReCheck(
            new SimpleMeterRegistry(), tombstoneStore,
            ChosenReadSource::servedByAuthority, () -> true, RoutingKey.PRIMARY)));

    @AfterEach
    void clearChosenSource() {
        ChosenReadSource.clear();
    }

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

    @Test
    void readAll_WhenNoReCheckIsWired_ReturnsTheDereferenceResultFromOneCall() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        AtomicInteger calls = new AtomicInteger(0);

        List<UUID> result = guard.readAll("planner", ids, requested -> {
            calls.incrementAndGet();
            return List.copyOf(requested);
        }, id -> id);

        assertThat(result).isEqualTo(ids);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void read_WhenThePrimaryServedTheRead_SkipsTheTombstoneGuard() {
        UUID id = UUID.randomUUID();
        tombstoneStore.writeTombstone(ByIdReadGuard.PLANNER_ENTITY_TYPE, id);

        String served = wiredGuard.read(ByIdReadGuard.PLANNER_ENTITY_TYPE, id, () -> {
            ChosenReadSource.record(ReadSourceProperties.PRIMARY, true);
            return "planner";
        });

        assertThat(served).isEqualTo("planner");
    }

    @Test
    void read_WhenTheReplicaServedTheRead_MasksATombstonedRow() {
        UUID id = UUID.randomUUID();
        tombstoneStore.writeTombstone(ByIdReadGuard.PLANNER_ENTITY_TYPE, id);

        assertRejected(() -> wiredGuard.read(ByIdReadGuard.PLANNER_ENTITY_TYPE, id, () -> {
            ChosenReadSource.record("seoul-replica", false);
            return "planner";
        })).withCode("NOT_FOUND");
    }
}
