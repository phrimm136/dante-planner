package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.api.Visibility;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.RecordingRestrictionCheck;
import org.danteplanner.backend.support.RecordingRestrictionCheck.Decision;
import org.danteplanner.backend.shared.restriction.UnrestrictedMember;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;

import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Unit tests for PlannerSubscriptionService.
 *
 * <p>Tests subscription business logic in isolation using Mockito mocks.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlannerSubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private static final long PLANNER_OWNER_ID = 41L;

    @Mock
    private PlannerSubscriptionRepository subscriptionRepository;

    @Mock
    private PlannerRepository plannerRepository;

    private PlannerSubscriptionService subscriptionService;

    private RecordingRestrictionCheck restrictionCheck;

    private User testUser;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        FakeUsers users = new FakeUsers();
        restrictionCheck = new RecordingRestrictionCheck(users);
        subscriptionService = new PlannerSubscriptionService(
                subscriptionRepository, new PlannerAccessGuard(null, plannerRepository), restrictionCheck,
                Clock.fixed(NOW, ZoneOffset.UTC));

        testUser = users.add(TestDataFactory.unsavedUser(1L));

        plannerId = UUID.randomUUID();
    }

    private void published() {
        when(plannerRepository.findThread(plannerId))
                .thenReturn(Optional.of(new PlannerThread(plannerId, PLANNER_OWNER_ID, Visibility.SHOWN)));
    }

    private void toggles(int affectedRows, boolean entered) {
        published();
        when(subscriptionRepository.toggle(testUser.getId(), plannerId, NOW)).thenReturn(affectedRows);
        when(subscriptionRepository.findEnabledOf(testUser.getId(), plannerId)).thenReturn(Optional.of(entered));
    }

    @Nested
    class ToggleSubscriptionTests {

        @Test
        void toggleSubscription_WhenTheRowEndsOn_ChecksTheRestriction() {
            toggles(1, true);

            assertThat(subscriptionService.toggleSubscription(testUser.getId(), plannerId)).isTrue();

            assertThat(restrictionCheck.decisions()).containsExactly(Decision.CHECKED);
            assertThat(restrictionCheck.clearances()).singleElement().isInstanceOfSatisfying(
                    UnrestrictedMember.class, member -> assertThat(member.userId()).isEqualTo(testUser.getId()));
        }

        @Test
        void toggleSubscription_WhenTheRowEndsOff_IsAPrivateDecision() {
            toggles(2, false);

            assertThat(subscriptionService.toggleSubscription(testUser.getId(), plannerId)).isFalse();

            assertThat(restrictionCheck.decisions()).containsExactly(Decision.PRIVATE);
        }

        @Test
        void toggleSubscription_WhenPlannerMissingOrUnpublished_WritesNothing() {
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findThread(nonExistentId)).thenReturn(Optional.empty());

            assertRejected(() -> subscriptionService.toggleSubscription(testUser.getId(), nonExistentId))
                    .withCode("PLANNER_NOT_FOUND");

            verifyNoInteractions(subscriptionRepository);
        }

        @Test
        void toggleSubscription_WhenBannedAndTurningOn_Refuses() {
            TestDataFactory.ban(testUser, Instant.now(), null);
            toggles(1, true);

            assertRejected(() -> subscriptionService.toggleSubscription(testUser.getId(), plannerId))
                    .withCode("USER_BANNED");
        }

        @Test
        void toggleSubscription_WhenTimedOutAndTurningOn_Refuses() {
            TestDataFactory.timeOut(testUser, Instant.now().plusSeconds(3600));
            toggles(2, true);

            assertRejected(() -> subscriptionService.toggleSubscription(testUser.getId(), plannerId))
                    .withCode("USER_TIMED_OUT");
        }

        @Test
        void toggleSubscription_WhenBannedAndTurningOff_IsAllowedAsAPrivateDecision() {
            TestDataFactory.ban(testUser, Instant.now(), null);
            toggles(2, false);

            assertThat(subscriptionService.toggleSubscription(testUser.getId(), plannerId)).isFalse();

            assertThat(restrictionCheck.decisions()).containsExactly(Decision.PRIVATE);
        }
    }

    @Test
    void subscribe_WhenPublished_WritesTheSubscribedState() {
        published();

        subscriptionService.subscribe(testUser.getId(), plannerId);

        verify(subscriptionRepository).subscribe(testUser.getId(), plannerId, NOW);
        verifyNoMoreInteractions(subscriptionRepository);
    }

    @Test
    void unsubscribe_WhenThePlannerIsNotReadable_WritesTheUnsubscribedStateWithoutAskingForIt() {
        subscriptionService.unsubscribe(testUser.getId(), plannerId);

        verify(subscriptionRepository).unsubscribe(testUser.getId(), plannerId);
        verifyNoMoreInteractions(subscriptionRepository);
        verifyNoInteractions(plannerRepository);
    }

    @Test
    void subscribe_WhenPlannerMissing_WritesNothing() {
        when(plannerRepository.findThread(plannerId)).thenReturn(Optional.empty());

        assertRejected(() -> subscriptionService.subscribe(testUser.getId(), plannerId)).withCode("PLANNER_NOT_FOUND");

        verifyNoInteractions(subscriptionRepository);
    }

    @Nested
    class IsSubscribedTests {

        @Test
        void isSubscribed_WhenSubscribedAndEnabled_ReturnsTrue() {
            when(subscriptionRepository.findEnabledOf(testUser.getId(), plannerId)).thenReturn(Optional.of(true));

            assertThat(subscriptionService.isSubscribed(testUser.getId(), plannerId)).isTrue();
        }

        @Test
        void isSubscribed_WhenSubscribedButDisabled_ReturnsFalse() {
            when(subscriptionRepository.findEnabledOf(testUser.getId(), plannerId)).thenReturn(Optional.of(false));

            assertThat(subscriptionService.isSubscribed(testUser.getId(), plannerId)).isFalse();
        }

        @Test
        void isSubscribed_WhenNotSubscribed_ReturnsFalse() {
            when(subscriptionRepository.findEnabledOf(testUser.getId(), plannerId)).thenReturn(Optional.empty());

            assertThat(subscriptionService.isSubscribed(testUser.getId(), plannerId)).isFalse();
        }
    }

    @Test
    void createSubscription_WhenCalled_InsertsOnlyIfAbsent() {
        subscriptionService.createSubscription(testUser.getId(), plannerId);

        verify(subscriptionRepository).insertIfAbsent(testUser.getId(), plannerId, NOW);
        verifyNoMoreInteractions(subscriptionRepository);
    }
}
