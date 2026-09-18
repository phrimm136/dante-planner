package org.danteplanner.backend.planner.service;

import java.util.Optional;
import java.util.UUID;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
        accessGuard = new PlannerAccessGuard(userService, plannerRepository);
        plannerId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("checkPublished")
    class CheckPublished {

        @Test
        @DisplayName("passes without loading the aggregate when the planner is published")
        void checkPublished_WhenPublished_DoesNotLoadAggregate() {
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);

            assertThatCode(() -> accessGuard.checkPublished(plannerId)).doesNotThrowAnyException();

            verify(plannerRepository, never()).findPublishedAggregate(any());
        }

        @Test
        @DisplayName("throws PlannerNotFoundException when no published planner carries the id")
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
    @DisplayName("requirePublished")
    class RequirePublished {

        @Test
        @DisplayName("throws the same exception as checkPublished when the planner is absent")
        void requirePublished_WhenUnpublishedOrMissing_ThrowsPlannerNotFound() {
            when(plannerRepository.findPublishedAggregate(plannerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accessGuard.requirePublished(plannerId))
                    .isInstanceOf(PlannerNotFoundException.class)
                    .hasMessage("Planner not found with id: " + plannerId);
        }

        @Test
        @DisplayName("returns the loaded aggregate when the planner is published")
        void requirePublished_WhenPublished_ReturnsAggregate() {
            Planner planner = new Planner();
            when(plannerRepository.findPublishedAggregate(plannerId)).thenReturn(Optional.of(planner));

            assertThat(accessGuard.requirePublished(plannerId)).isSameAs(planner);
        }
    }
}
