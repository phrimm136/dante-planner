package org.danteplanner.backend.entity;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PublicationChange;

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

    private static Planner planner(UUID id, PlannerContent content,
            PlannerPublication publication, PlannerModeration moderation) {
        Planner planner = Planner.builder()
                .id(id)
                .build();
        planner.attach(content, publication, moderation);
        return planner;
    }

    @Test
    void takeDown_WhenPublished_UnpublishesAndStamps() {
        Planner planner = planner(UUID.randomUUID(),
                PlannerContent.builder().build(),
                PlannerPublication.builder().published(true).build(),
                PlannerModeration.builder().build());

        planner.takeDown();

        assertThat(planner.getTakenDownAt()).isNotNull();
        assertThat(planner.getPublished()).isFalse();
    }

    @Test
    void publish_WhenTakenDown_Throws() {
        UUID plannerId = UUID.randomUUID();
        Planner planner = planner(plannerId,
                PlannerContent.builder().build(),
                PlannerPublication.builder().published(false).build(),
                PlannerModeration.builder().takenDownAt(Instant.now()).build());

        assertThatThrownBy(planner::publish)
                .isInstanceOf(PlannerForbiddenException.class)
                .extracting(ex -> ((PlannerForbiddenException) ex).getPlannerId())
                .isEqualTo(plannerId);
    }

    @Test
    void publish_WhenFirstPublish_StampsFirstPublishedAtOnce() {
        Planner planner = planner(UUID.randomUUID(),
                PlannerContent.builder().build(),
                PlannerPublication.builder().published(false).build(),
                PlannerModeration.builder().build());

        planner.publish();
        Instant firstStamp = planner.getFirstPublishedAt();

        assertThat(planner.getPublished()).isTrue();
        assertThat(firstStamp).isNotNull();

        planner.unpublish();
        assertThat(planner.getPublished()).isFalse();

        planner.publish();
        assertThat(planner.getPublished()).isTrue();
        assertThat(planner.getFirstPublishedAt()).isEqualTo(firstStamp);
    }

    @Test
    void publish_WhenAlreadyPublished_ChangesNothing() {
        Planner planner = planner(UUID.randomUUID(),
                PlannerContent.builder().build(),
                PlannerPublication.builder().published(false).build(),
                PlannerModeration.builder().build());

        planner.publish();
        Instant firstStamp = planner.getFirstPublishedAt();

        assertThat(planner.publish()).isEqualTo(PublicationChange.NONE);

        assertThat(planner.getPublished()).isTrue();
        assertThat(planner.getFirstPublishedAt())
                .as("re-applying the state a planner already holds is a no-op, stamp included")
                .isEqualTo(firstStamp);
    }

    @Test
    void hideFromRecommended_WhenCalled_SetsAllFourFields() {
        Planner planner = planner(UUID.randomUUID(),
                PlannerContent.builder().build(),
                PlannerPublication.builder().build(),
                PlannerModeration.builder().build());

        planner.hideFromRecommended(42L, "spam");

        assertThat(planner.getHiddenFromRecommended()).isTrue();
        assertThat(planner.getModeration().getHiddenByModeratorId()).isEqualTo(42L);
        assertThat(planner.getModeration().getHiddenReason()).isEqualTo("spam");
        assertThat(planner.getModeration().getHiddenAt()).isNotNull();
    }

    @Test
    void unhideFromRecommended_WhenCalled_ClearsAllFourFields() {
        Planner planner = planner(UUID.randomUUID(),
                PlannerContent.builder().build(),
                PlannerPublication.builder().build(),
                PlannerModeration.builder()
                        .hiddenFromRecommended(true)
                        .hiddenByModeratorId(42L)
                        .hiddenReason("spam")
                        .hiddenAt(Instant.now())
                        .build());

        planner.unhideFromRecommended();

        assertThat(planner.getHiddenFromRecommended()).isFalse();
        assertThat(planner.getModeration().getHiddenByModeratorId()).isNull();
        assertThat(planner.getModeration().getHiddenReason()).isNull();
        assertThat(planner.getModeration().getHiddenAt()).isNull();
    }

    @Test
    void recordSave_WhenCalled_IncrementsSyncVersion() {
        Planner planner = planner(UUID.randomUUID(),
                PlannerContent.builder().syncVersion(5L).build(),
                PlannerPublication.builder().build(),
                PlannerModeration.builder().build());

        planner.recordSave();

        assertThat(planner.getSyncVersion()).isEqualTo(6L);
    }

    @Test
    void unpublish_WhenPublished_OnlyClearsPublished() {
        Instant takenDownAt = Instant.now();
        Planner planner = planner(UUID.randomUUID(),
                PlannerContent.builder().build(),
                PlannerPublication.builder().published(true).build(),
                PlannerModeration.builder().takenDownAt(takenDownAt).build());

        planner.unpublish();

        assertThat(planner.getPublished()).isFalse();
        assertThat(planner.getTakenDownAt()).isEqualTo(takenDownAt);
    }
}
