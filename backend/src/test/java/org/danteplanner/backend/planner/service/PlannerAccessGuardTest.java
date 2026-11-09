package org.danteplanner.backend.planner.service;

import java.util.Optional;
import java.util.UUID;

import org.danteplanner.backend.planner.api.PlannerMiss;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.shared.freshness.ScopedRead;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the published-visibility half of {@link PlannerAccessGuard}: the
 * {@code checkPublished} form and the loading {@code requirePublished} form load the row unfiltered
 * and answer an absent row and a hidden one with the same not-found, distinguished only by the
 * scopes the miss declares.
 */
@ExtendWith(MockitoExtension.class)
class PlannerAccessGuardTest {

    private static final long OWNER_ID = 41L;

    @Mock
    private PlannerRepository plannerRepository;

    private PlannerAccessGuard accessGuard;

    private UUID plannerId;

    @BeforeEach
    void setUp() {
        accessGuard = new PlannerAccessGuard(null, plannerRepository);
        plannerId = UUID.randomUUID();
    }

    private PlannerNotFoundException missOf(Runnable read) {
        Throwable thrown = catchThrowable(read::run);
        assertThat(thrown).isInstanceOf(PlannerNotFoundException.class)
                .hasMessage("Planner not found with id: " + plannerId);
        return (PlannerNotFoundException) thrown;
    }

    @Nested
    class CheckPublished {

        @Test
        void checkPublished_WhenVisible_ServesTheEntityAndAuthorScopesWithoutLoadingTheAggregate() {
            when(plannerRepository.findThread(plannerId))
                    .thenReturn(Optional.of(new PlannerThread(plannerId, OWNER_ID, true, false)));

            ScopedRead<UUID> read = accessGuard.checkPublished(plannerId);

            assertThat(read.value()).isEqualTo(plannerId);
            assertThat(read.scopes()).containsExactlyInAnyOrder(
                    new FreshnessScope.Entity(plannerId), new FreshnessScope.Author(OWNER_ID));
            verify(plannerRepository, never()).findAggregateWithAuthor(any());
        }

        @Test
        void checkPublished_WhenNoRow_ThrowsAbsent() {
            when(plannerRepository.findThread(plannerId)).thenReturn(Optional.empty());

            assertThat(missOf(() -> accessGuard.checkPublished(plannerId)).miss())
                    .isEqualTo(new PlannerMiss.Absent(plannerId));
        }

        @ParameterizedTest
        @CsvSource({"false, false", "true, true", "false, true"})
        void checkPublished_WhenRowIsNotVisible_ThrowsHiddenNamingTheOwner(boolean published, boolean ownerDeactivated) {
            when(plannerRepository.findThread(plannerId))
                    .thenReturn(Optional.of(new PlannerThread(plannerId, OWNER_ID, published, ownerDeactivated)));

            PlannerNotFoundException miss = missOf(() -> accessGuard.checkPublished(plannerId));

            assertThat(miss.miss()).isEqualTo(new PlannerMiss.Hidden(plannerId, OWNER_ID));
            assertThat(miss.scopes()).containsExactlyInAnyOrder(
                    new FreshnessScope.Entity(plannerId), new FreshnessScope.Author(OWNER_ID));
        }
    }

    @Nested
    class RequirePublished {

        private final User owner = TestDataFactory.unsavedUser(OWNER_ID);

        @Test
        void requirePublished_WhenNoRow_ThrowsAbsent() {
            when(plannerRepository.findAggregateWithAuthor(plannerId)).thenReturn(Optional.empty());

            assertThat(missOf(() -> accessGuard.requirePublished(plannerId)).miss())
                    .isEqualTo(new PlannerMiss.Absent(plannerId));
        }

        @Test
        void requirePublished_WhenUnpublished_ThrowsHiddenNamingTheOwner() {
            Planner planner = TestDataFactory.planner(owner).id(plannerId).published(false).build();
            when(plannerRepository.findAggregateWithAuthor(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(owner))));

            assertThat(missOf(() -> accessGuard.requirePublished(plannerId)).miss())
                    .isEqualTo(new PlannerMiss.Hidden(plannerId, OWNER_ID));
        }

        @Test
        void requirePublished_WhenPublished_ReturnsAggregate() {
            Planner planner = TestDataFactory.planner(owner).id(plannerId).published(true).build();
            when(plannerRepository.findAggregateWithAuthor(plannerId))
                    .thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(owner))));

            assertThat(accessGuard.requirePublished(plannerId)).isSameAs(planner);
        }
    }
}
