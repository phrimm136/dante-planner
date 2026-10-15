package org.danteplanner.backend.support;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;
import org.danteplanner.backend.support.RecordingTombstoneStore.Clears;
import org.danteplanner.backend.support.RecordingTombstoneStore.Tombstone;
import org.danteplanner.backend.support.RecordingTombstoneStore.Write;
import org.danteplanner.backend.support.RecordingTombstoneStore.Writes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingTombstoneStoreTest {

    private static final String PLANNER = ByIdReadGuard.PLANNER_ENTITY_TYPE;
    private static final String PUBLISHED = ByIdReadGuard.PUBLISHED_PLANNER_SCOPE;

    private final RecordingTombstoneStore store = new RecordingTombstoneStore();
    private final PrimaryReCheck reCheck = new PrimaryReCheck(new SimpleMeterRegistry(), store,
            () -> false, () -> true, RoutingKey.BULKHEAD);

    @Test
    void readWithReCheck_WhenTheRowIsTombstoned_TreatsItAsGone() {
        UUID id = UUID.randomUUID();

        store.writeTombstone(PLANNER, id);

        assertThatThrownBy(() -> reCheck.readWithReCheck(PLANNER, id, () -> "row"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage(PLANNER + " not found with id: " + id);
    }

    @Test
    void readAllWithReCheck_WhenOneOfTwoRowsIsTombstoned_ServesTheOther() {
        UUID kept = UUID.randomUUID();
        UUID gone = UUID.randomUUID();

        store.writeTombstones(PLANNER, List.of(gone));

        assertThat(reCheck.readAllWithReCheck(PLANNER, List.of(kept, gone), List::copyOf, id -> id))
                .containsExactly(kept);
    }

    @Test
    void readWithReCheck_WhenTheTombstoneIsCleared_ServesTheRow() {
        UUID id = UUID.randomUUID();
        store.writeTombstone(PLANNER, id);

        store.clearTombstones(PLANNER, List.of(id));

        assertThat(reCheck.readWithReCheck(PLANNER, id, () -> "row")).isEqualTo("row");
    }

    @Test
    void calls_WhenEachVariantRuns_RecordWhichVariantInOrder() {
        UUID id = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        store.writeTombstone(PLANNER, id);
        store.writeTombstones(PUBLISHED, List.of(id, other));
        store.clearTombstones(PUBLISHED, List.of(other));

        assertThat(store.calls()).containsExactly(
                new Write(PLANNER, id),
                new Writes(PUBLISHED, List.of(id, other)),
                new Clears(PUBLISHED, List.of(other)));
        assertThat(store.standing()).containsExactlyInAnyOrder(
                new Tombstone(PLANNER, id), new Tombstone(PUBLISHED, id));
    }
}
