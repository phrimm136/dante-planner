package org.danteplanner.backend.support;

import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.RedisConnectionFailureException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.shared.readpath.ByIdReadGuard;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;
import org.danteplanner.backend.support.RecordingTombstoneStore.ClearOrThrow;
import org.danteplanner.backend.support.RecordingTombstoneStore.Failed;
import org.danteplanner.backend.support.RecordingTombstoneStore.Operation;
import org.danteplanner.backend.support.RecordingTombstoneStore.Tombstone;
import org.danteplanner.backend.support.RecordingTombstoneStore.Write;
import org.danteplanner.backend.support.RecordingTombstoneStore.WriteOrThrow;
import org.danteplanner.backend.support.RecordingTombstoneStore.Writes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ScriptedFailuresCheck.class)
class RecordingTombstoneStoreTest {

    private static final String PLANNER = ByIdReadGuard.PLANNER_ENTITY_TYPE;
    private static final String PUBLISHED = ByIdReadGuard.PUBLISHED_PLANNER_SCOPE;

    private final RecordingTombstoneStore store = new RecordingTombstoneStore();
    private final PrimaryReCheck reCheck = new PrimaryReCheck(new SimpleMeterRegistry(), store);

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

        store.clearTombstoneOrThrow(PLANNER, id);

        assertThat(reCheck.readWithReCheck(PLANNER, id, () -> "row")).isEqualTo("row");
    }

    @Test
    void calls_WhenEachVariantRuns_RecordWhichVariantInOrder() {
        UUID id = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        store.clearTombstoneOrThrow(PUBLISHED, id);
        store.writeTombstoneOrThrow(PLANNER, id);
        store.writeTombstone(PUBLISHED, other);
        store.writeTombstones(PUBLISHED, List.of(id, other));

        assertThat(store.calls()).containsExactly(
                new ClearOrThrow(PUBLISHED, id),
                new WriteOrThrow(PLANNER, id),
                new Write(PUBLISHED, other),
                new Writes(PUBLISHED, List.of(id, other)));
        assertThat(store.standing()).containsExactlyInAnyOrder(
                new Tombstone(PLANNER, id), new Tombstone(PUBLISHED, id), new Tombstone(PUBLISHED, other));
    }

    @Test
    void failNext_WhenScripted_FailsOneCallAndThenRecovers() {
        UUID id = UUID.randomUUID();
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");
        store.failNext(Operation.WRITE_OR_THROW, down);

        assertThatThrownBy(() -> store.writeTombstoneOrThrow(PLANNER, id))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessage("down");
        assertThat(store.standing()).isEmpty();
        store.writeTombstoneOrThrow(PLANNER, id);

        assertThat(store.calls())
                .containsExactly(new Failed(Operation.WRITE_OR_THROW, down), new WriteOrThrow(PLANNER, id));
    }
}
