package org.danteplanner.backend.entity;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PublicationChange;

import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tier-1 unit tests for the named state transitions on {@link Planner}.
 */
class PlannerTransitionTest {

    private static TestDataFactory.PlannerBuilder planner() {
        return TestDataFactory.planner(TestDataFactory.unsavedUser(UserRole.NORMAL));
    }

    @Test
    void takeDown_WhenPublished_UnpublishesAndStamps() {
        Planner planner = planner().build();
        planner.publish();

        planner.takeDown();

        assertThat(planner.getTakenDownAt()).isNotNull();
        assertThat(planner.isPublished()).isFalse();
    }

    @Test
    void publish_WhenTakenDown_Throws() {
        Planner planner = planner().build();
        planner.takeDown();

        assertThatThrownBy(planner::publish)
                .isInstanceOf(PlannerForbiddenException.class)
                .extracting(ex -> ((PlannerForbiddenException) ex).getPlannerId())
                .isEqualTo(planner.getId());
    }

    @Test
    void publish_WhenFirstPublish_StampsFirstPublishedAtOnce() {
        Planner planner = planner().build();

        planner.publish();
        Instant firstStamp = planner.getFirstPublishedAt();

        assertThat(planner.isPublished()).isTrue();
        assertThat(firstStamp).isNotNull();

        planner.unpublish();
        assertThat(planner.isPublished()).isFalse();

        planner.publish();
        assertThat(planner.isPublished()).isTrue();
        assertThat(planner.getFirstPublishedAt()).isEqualTo(firstStamp);
    }

    @Test
    void publish_WhenAlreadyPublished_ChangesNothing() {
        Planner planner = planner().build();

        planner.publish();
        Instant firstStamp = planner.getFirstPublishedAt();

        assertThat(planner.publish()).isEqualTo(PublicationChange.NONE);

        assertThat(planner.isPublished()).isTrue();
        assertThat(planner.getFirstPublishedAt())
                .as("re-applying the state a planner already holds is a no-op, stamp included")
                .isEqualTo(firstStamp);
    }

    @Test
    void hideFromRecommended_WhenCalled_SetsAllFourFields() {
        Planner planner = planner().build();

        planner.hideFromRecommended(42L, "spam");

        assertThat(planner.isHiddenFromRecommended()).isTrue();
        assertThat(planner.getModeration().getHiddenByModeratorId()).isEqualTo(42L);
        assertThat(planner.getModeration().getHiddenReason()).isEqualTo("spam");
        assertThat(planner.getModeration().getHiddenAt()).isNotNull();
    }

    @Test
    void unhideFromRecommended_WhenCalled_ClearsAllFourFields() {
        Planner planner = planner().build();
        planner.hideFromRecommended(42L, "spam");

        planner.unhideFromRecommended();

        assertThat(planner.isHiddenFromRecommended()).isFalse();
        assertThat(planner.getModeration().getHiddenByModeratorId()).isNull();
        assertThat(planner.getModeration().getHiddenReason()).isNull();
        assertThat(planner.getModeration().getHiddenAt()).isNull();
    }

    @Test
    void recordSave_WhenCalled_IncrementsSyncVersion() {
        Planner planner = planner().syncVersion(5).build();

        planner.recordSave();

        assertThat(planner.getSyncVersion()).isEqualTo(6L);
    }

    @Test
    void unpublish_WhenTakenDown_KeepsTakenDownAt() {
        Planner planner = planner().build();
        planner.publish();
        planner.takeDown();
        Instant takenDownAt = planner.getTakenDownAt();

        planner.unpublish();

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.getTakenDownAt()).isEqualTo(takenDownAt);
    }
}
