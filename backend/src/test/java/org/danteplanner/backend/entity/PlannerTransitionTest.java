package org.danteplanner.backend.entity;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PublicationOutcome;

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

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private static TestDataFactory.PlannerBuilder planner() {
        return TestDataFactory.planner(TestDataFactory.unsavedUser(UserRole.NORMAL));
    }

    @Test
    void takeDown_WhenPublished_UnpublishesAndStamps() {
        Planner planner = planner().build();
        planner.publish(NOW);

        planner.takeDown(Instant.now());

        assertThat(planner.getTakenDownAt()).isNotNull();
        assertThat(planner.isPublished()).isFalse();
    }

    @Test
    void publish_WhenTakenDown_Throws() {
        Planner planner = planner().build();
        planner.takeDown(Instant.now());

        assertThatThrownBy(() -> planner.publish(NOW))
                .isInstanceOf(PlannerForbiddenException.class)
                .extracting(ex -> ((PlannerForbiddenException) ex).getPlannerId())
                .isEqualTo(planner.getId());
    }

    @Test
    void publish_WhenFirstPublish_StampsFirstPublishedAtOnce() {
        Planner planner = planner().build();

        planner.publish(NOW);
        Instant firstStamp = planner.getFirstPublishedAt();

        assertThat(planner.isPublished()).isTrue();
        assertThat(firstStamp).isNotNull();

        planner.unpublish();
        assertThat(planner.isPublished()).isFalse();

        planner.publish(NOW);
        assertThat(planner.isPublished()).isTrue();
        assertThat(planner.getFirstPublishedAt()).isEqualTo(firstStamp);
    }

    @Test
    void publish_WhenAlreadyPublished_ChangesNothing() {
        Planner planner = planner().build();

        planner.publish(NOW);
        Instant firstStamp = planner.getFirstPublishedAt();

        assertThat(planner.publish(NOW)).isEqualTo(PublicationOutcome.NONE);

        assertThat(planner.isPublished()).isTrue();
        assertThat(planner.getFirstPublishedAt())
                .as("re-applying the state a planner already holds is a no-op, stamp included")
                .isEqualTo(firstStamp);
    }

    @Test
    void publish_WhenNeverPublished_StampsNowAsTheFirstPublish() {
        PlannerPublication.Transition transition = PlannerPublication.publish(false, new PlannerPublication.FirstPublished.Never(), NOW);

        assertThat(transition).isEqualTo(new PlannerPublication.Transition(true, new PlannerPublication.FirstPublished.At(NOW), PublicationOutcome.FIRST_PUBLISH));
    }

    @Test
    void publish_WhenPublishedBefore_KeepsTheFirstStampAsARepublish() {
        PlannerPublication.Transition transition = PlannerPublication.publish(false, new PlannerPublication.FirstPublished.At(EARLIER), NOW);

        assertThat(transition).isEqualTo(new PlannerPublication.Transition(true, new PlannerPublication.FirstPublished.At(EARLIER), PublicationOutcome.REPUBLISH));
    }

    @Test
    void takeDown_WhenUnpublishedButNewlyModerated_IsAWithdrawal() {
        PlannerPublication.Transition transition = PlannerPublication.takeDown(false, new PlannerPublication.FirstPublished.At(EARLIER), true);

        assertThat(transition).isEqualTo(new PlannerPublication.Transition(false, new PlannerPublication.FirstPublished.At(EARLIER), PublicationOutcome.WITHDRAWN));
    }

    @Test
    void takeDown_WhenUnpublishedAndAlreadyTakenDown_ChangesNothing() {
        PlannerPublication.Transition transition = PlannerPublication.takeDown(false, new PlannerPublication.FirstPublished.At(EARLIER), false);

        assertThat(transition.outcome()).isEqualTo(PublicationOutcome.NONE);
    }

    @Test
    void hideFromRecommended_WhenCalled_SetsAllFourFields() {
        Planner planner = planner().build();

        planner.hideFromRecommended(42L, "spam", Instant.now());

        assertThat(planner.isHiddenFromRecommended()).isTrue();
        assertThat(planner.getModeration().getHiddenByModeratorId()).isEqualTo(42L);
        assertThat(planner.getModeration().getHiddenReason()).isEqualTo("spam");
        assertThat(planner.getModeration().getHiddenAt()).isNotNull();
    }

    @Test
    void unhideFromRecommended_WhenCalled_ClearsAllFourFields() {
        Planner planner = planner().build();
        planner.hideFromRecommended(42L, "spam", Instant.now());

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
        planner.publish(NOW);
        planner.takeDown(Instant.now());
        Instant takenDownAt = planner.getTakenDownAt();

        planner.unpublish();

        assertThat(planner.isPublished()).isFalse();
        assertThat(planner.getTakenDownAt()).isEqualTo(takenDownAt);
    }
}
