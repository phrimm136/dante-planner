package org.danteplanner.backend.entity;

import org.danteplanner.backend.planner.entity.Listing;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.Publication.Draft;
import org.danteplanner.backend.planner.entity.Publication.Published;
import org.danteplanner.backend.planner.entity.Publication.Unpublished;
import org.danteplanner.backend.planner.entity.PublishOutcome;
import org.danteplanner.backend.planner.entity.Takedown;
import org.danteplanner.backend.planner.entity.WithdrawalOutcome;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tier-1 unit tests for the states of a planner and their transitions.
 */
class PlannerTransitionTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private static final UUID PLANNER_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void publication_WhenParsed_IsTheStateOfItsTwoColumns() {
        assertThat(Publication.unlisted(null)).isEqualTo(new Draft());
        assertThat(Publication.listed(PLANNER_ID, EARLIER)).isEqualTo(new Published(EARLIER));
        assertThat(Publication.unlisted(EARLIER)).isEqualTo(new Unpublished(EARLIER));
    }

    @Test
    void publication_WhenPublishedWithoutATime_Throws() {
        assertThatThrownBy(() -> Publication.listed(PLANNER_ID, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("planner " + PLANNER_ID + " is listed without a publication time");
    }

    @Test
    void published_WhenNeverPublished_StampsNow() {
        assertThat(new Draft().published(NOW)).isEqualTo(new Published(NOW));
    }

    @Test
    void published_WhenPublishedBefore_KeepsTheFirstStamp() {
        assertThat(new Unpublished(EARLIER).published(NOW)).isEqualTo(new Published(EARLIER));
        assertThat(new Published(EARLIER).published(NOW)).isEqualTo(new Published(EARLIER));
    }

    @Test
    void withdrawn_WhenPublished_KeepsTheFirstStamp() {
        assertThat(new Published(EARLIER).withdrawn()).isEqualTo(new Unpublished(EARLIER));
        assertThat(new Unpublished(EARLIER).withdrawn()).isEqualTo(new Unpublished(EARLIER));
        assertThat(new Draft().withdrawn()).isEqualTo(new Draft());
    }

    @Test
    void isPublished_WhenAsked_IsTrueOnlyOfPublished() {
        assertThat(new Published(EARLIER).isPublished()).isTrue();
        assertThat(new Unpublished(EARLIER).isPublished()).isFalse();
        assertThat(new Draft().isPublished()).isFalse();
    }

    @Test
    void publishOutcome_WhenNoRowMatched_IsNoneAndKeepsTheState() {
        Publication before = new Draft();

        assertThat(PublishOutcome.FIRST_PUBLISH.when(WriteOutcome.NOTHING)).isEqualTo(PublishOutcome.NONE);
        assertThat(PublishOutcome.REPUBLISH.when(WriteOutcome.APPLIED)).isEqualTo(PublishOutcome.REPUBLISH);
        assertThat(PublishOutcome.NONE.after(before, NOW)).isEqualTo(before);
        assertThat(PublishOutcome.FIRST_PUBLISH.after(before, NOW)).isEqualTo(new Published(NOW));
    }

    @Test
    void withdrawalOutcome_WhenEitherWriteApplied_IsAWithdrawal() {
        WithdrawalOutcome applied = WithdrawalOutcome.of(WriteOutcome.APPLIED);
        WithdrawalOutcome nothing = WithdrawalOutcome.of(WriteOutcome.NOTHING);

        assertThat(applied.or(nothing)).isEqualTo(WithdrawalOutcome.WITHDRAWN);
        assertThat(nothing.or(applied)).isEqualTo(WithdrawalOutcome.WITHDRAWN);
        assertThat(nothing.or(nothing)).isEqualTo(WithdrawalOutcome.NONE);
    }

    @Test
    void listing_WhenHidden_HoldsTheModeratorReasonAndTime() {
        assertThat(Listing.hidden(PLANNER_ID, 42L, "spam", NOW)).isEqualTo(new Listing.Hidden(42L, "spam", NOW));
        assertThat(Listing.hidden(PLANNER_ID, null, "spam", NOW))
                .as("the moderator's account was deleted")
                .isEqualTo(new Listing.Hidden(null, "spam", NOW));
    }

    @Test
    void listing_WhenHiddenWithoutAReasonOrATime_Throws() {
        assertThatThrownBy(() -> Listing.hidden(PLANNER_ID, null, null, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is hidden without a reason or a time");
        assertThatThrownBy(() -> Listing.hidden(PLANNER_ID, null, "spam", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is hidden without a reason or a time");
    }

    @Test
    void takedown_WhenParsed_IsClearOrTakenDown() {
        assertThat(Takedown.of(null)).isEqualTo(new Takedown.Clear());
        assertThat(Takedown.of(NOW)).isEqualTo(new Takedown.TakenDown(NOW));
    }
}
