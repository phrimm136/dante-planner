package org.danteplanner.backend.entity;
import org.danteplanner.backend.planner.entity.Planner;

import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tier-1 unit tests for the named state transitions on {@link Planner}.
 */
class PlannerTransitionTest {

    @Test
    void takeDown_WhenPublished_UnpublishesAndStamps() {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .published(true)
                .build();

        planner.takeDown();

        assertThat(planner.getTakenDownAt()).isNotNull();
        assertThat(planner.getPublished()).isFalse();
    }

    @Test
    void togglePublished_WhenTakenDown_Throws() {
        UUID plannerId = UUID.randomUUID();
        Planner planner = Planner.builder()
                .id(plannerId)
                .published(false)
                .takenDownAt(Instant.now())
                .build();

        assertThatThrownBy(planner::togglePublished)
                .isInstanceOf(PlannerForbiddenException.class)
                .extracting(ex -> ((PlannerForbiddenException) ex).getPlannerId())
                .isEqualTo(plannerId);
    }

    @Test
    void togglePublished_WhenFirstPublish_StampsFirstPublishedAtOnce() {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .published(false)
                .build();

        boolean afterFirst = planner.togglePublished();
        Instant firstStamp = planner.getFirstPublishedAt();

        assertThat(afterFirst).isTrue();
        assertThat(firstStamp).isNotNull();

        boolean afterUnpublish = planner.togglePublished();
        assertThat(afterUnpublish).isFalse();

        boolean afterRepublish = planner.togglePublished();
        assertThat(afterRepublish).isTrue();
        assertThat(planner.getFirstPublishedAt()).isEqualTo(firstStamp);
    }

    @Test
    void hideFromRecommended_WhenCalled_SetsAllFourFields() {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .build();

        planner.hideFromRecommended(42L, "spam");

        assertThat(planner.getHiddenFromRecommended()).isTrue();
        assertThat(planner.getHiddenByModeratorId()).isEqualTo(42L);
        assertThat(planner.getHiddenReason()).isEqualTo("spam");
        assertThat(planner.getHiddenAt()).isNotNull();
    }

    @Test
    void unhideFromRecommended_WhenCalled_ClearsAllFourFields() {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .hiddenFromRecommended(true)
                .hiddenByModeratorId(42L)
                .hiddenReason("spam")
                .hiddenAt(Instant.now())
                .build();

        planner.unhideFromRecommended();

        assertThat(planner.getHiddenFromRecommended()).isFalse();
        assertThat(planner.getHiddenByModeratorId()).isNull();
        assertThat(planner.getHiddenReason()).isNull();
        assertThat(planner.getHiddenAt()).isNull();
    }

    @Test
    void recordSave_WhenCalled_IncrementsSyncVersionAndStampsSavedAt() {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .syncVersion(5L)
                .build();

        planner.recordSave();

        assertThat(planner.getSyncVersion()).isEqualTo(6L);
        assertThat(planner.getSavedAt()).isNotNull();
    }

    @Test
    void unpublish_WhenPublished_OnlyClearsPublished() {
        Instant takenDownAt = Instant.now();
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .published(true)
                .takenDownAt(takenDownAt)
                .build();

        planner.unpublish();

        assertThat(planner.getPublished()).isFalse();
        assertThat(planner.getTakenDownAt()).isEqualTo(takenDownAt);
    }
}
