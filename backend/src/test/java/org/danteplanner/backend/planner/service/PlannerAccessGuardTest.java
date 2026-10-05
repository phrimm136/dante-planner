package org.danteplanner.backend.planner.service;

import java.util.Optional;
import org.danteplanner.backend.user.service.UsersApiAdapter;
import java.util.UUID;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PublishedPlannerRow;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the published-visibility half of {@link PlannerAccessGuard}: the void
 * {@code checkPublished} form and the loading {@code requirePublished} form answer the same
 * question with the same failure.
 */
@ExtendWith(MockitoExtension.class)
class PlannerAccessGuardTest {

    @Mock
    private UserService userService;

    @Mock
    private PlannerRepository plannerRepository;

    private PlannerAccessGuard accessGuard;

    private UUID plannerId;

    @BeforeEach
    void setUp() {
        accessGuard = new PlannerAccessGuard(new UsersApiAdapter(userService, null), plannerRepository);
        plannerId = UUID.randomUUID();
    }

    @Nested
    class CheckPublished {

        @Test
        void checkPublished_WhenPublished_DoesNotLoadAggregate() {
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);

            assertThatCode(() -> accessGuard.checkPublished(plannerId)).doesNotThrowAnyException();

            verify(plannerRepository, never()).findPublishedAggregate(any());
        }

        @Test
        void checkPublished_WhenUnpublishedOrMissing_ThrowsPlannerNotFound() {
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(false);

            assertThatThrownBy(() -> accessGuard.checkPublished(plannerId))
                    .isInstanceOf(PlannerNotFoundException.class)
                    .hasMessage("Planner not found with id: " + plannerId)
                    .extracting(e -> ((PlannerNotFoundException) e).getPlannerId())
                    .isEqualTo(plannerId);
        }
    }

    @Nested
    class RequirePublished {

        @Test
        void requirePublished_WhenUnpublishedOrMissing_ThrowsPlannerNotFound() {
            when(plannerRepository.findPublishedAggregate(plannerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accessGuard.requirePublished(plannerId))
                    .isInstanceOf(PlannerNotFoundException.class)
                    .hasMessage("Planner not found with id: " + plannerId);
        }

        @Test
        void requirePublished_WhenPublished_ReturnsAggregate() {
            Planner planner = new Planner();
            when(plannerRepository.findPublishedAggregate(plannerId)).thenReturn(Optional.of(new PublishedPlannerRow(planner, TestDataFactory.profileOf(TestDataFactory.unsavedUser(1L)))));

            assertThat(accessGuard.requirePublished(plannerId)).isSameAs(planner);
        }
    }
}
