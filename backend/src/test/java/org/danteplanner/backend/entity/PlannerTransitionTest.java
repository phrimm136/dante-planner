package org.danteplanner.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToOne;
import org.danteplanner.backend.planner.entity.Listing;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.Publication.Draft;
import org.danteplanner.backend.planner.entity.Publication.Published;
import org.danteplanner.backend.planner.entity.Publication.Unpublished;
import org.danteplanner.backend.planner.entity.PublishOutcome;
import org.danteplanner.backend.planner.entity.WithdrawalOutcome;
import org.danteplanner.backend.shared.failure.WriteOutcome;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Tier-1 unit tests for the states of a {@link Planner} and their transitions.
 */
class PlannerTransitionTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");

    private static TestDataFactory.PlannerBuilder planner() {
        return TestDataFactory.planner(TestDataFactory.unsavedUser(UserRole.NORMAL));
    }

    @Test
    void publicationState_WhenParsed_IsTheStateOfItsTwoColumns() {
        assertThat(planner().build().publicationState()).isEqualTo(new Draft());
        assertThat(planner().published(true).firstPublishedAt(EARLIER).build().publicationState())
                .isEqualTo(new Published(EARLIER));
        assertThat(planner().firstPublishedAt(EARLIER).build().publicationState())
                .isEqualTo(new Unpublished(EARLIER));
    }

    @Test
    void publicationState_WhenPublishedWithoutATime_Throws() {
        Planner planner = planner().build();
        planner.attach(planner.getContent(),
                PlannerPublication.builder().planner(planner).published(true).build(), planner.getModeration());

        assertThatThrownBy(planner::publicationState)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("planner " + planner.getId() + " is listed without a publication time");
    }

    @Test
    void requirePublishable_WhenTakenDown_Throws() {
        Planner planner = planner().takenDownAt(EARLIER).build();

        assertRejected(planner::requirePublishable)
                .withCode("PLANNER_FORBIDDEN")
                .satisfies(rejection -> assertThat(rejection.getMessage())
                        .isEqualTo("User is not authorized to modify planner with id: " + planner.getId()));
    }

    @Test
    void requirePublishable_WhenNotTakenDown_HoldsThePublication() {
        assertThat(planner().firstPublishedAt(EARLIER).build().requirePublishable().publication())
                .isEqualTo(new Unpublished(EARLIER));
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
    void listing_WhenParsed_IsListedOrHidden() {
        assertThat(Listing.of(PlannerModeration.builder().build())).isEqualTo(new Listing.Listed());
        assertThat(Listing.of(hidden().hiddenByModeratorId(42L).hiddenReason("spam").hiddenAt(NOW).build()))
                .isEqualTo(new Listing.Hidden(42L, "spam", NOW));
        assertThat(Listing.of(hidden().hiddenReason("spam").hiddenAt(NOW).build()))
                .as("the moderator's account was deleted")
                .isEqualTo(new Listing.Hidden(null, "spam", NOW));
    }

    @Test
    void listing_WhenHiddenWithoutAReasonOrATime_Throws() {
        for (PlannerModeration row : List.of(hidden().hiddenAt(NOW).build(), hidden().hiddenReason("spam").build())) {
            assertThatThrownBy(() -> Listing.of(row))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("is hidden without a reason or a time");
        }
    }

    private static PlannerModeration.PlannerModerationBuilder hidden() {
        return PlannerModeration.builder().hiddenFromRecommended(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"publication", "moderation"})
    void satellite_WhenMapped_CascadesOnlyTheInsert(String field) throws NoSuchFieldException {
        OneToOne mapping = Planner.class.getDeclaredField(field).getAnnotation(OneToOne.class);

        assertThat(mapping.cascade()).containsExactly(CascadeType.PERSIST);
        assertThat(mapping.orphanRemoval()).isFalse();
    }

    @Test
    void recordSave_WhenCalled_IncrementsSyncVersion() {
        Planner planner = planner().syncVersion(5).build();

        planner.recordSave();

        assertThat(planner.getSyncVersion()).isEqualTo(6L);
    }
}
