package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.user.service.UsersApiAdapter;

import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlannerSubscriptionService.
 *
 * <p>Tests subscription business logic in isolation using Mockito mocks.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlannerSubscriptionServiceTest {

    @Mock
    private PlannerSubscriptionRepository subscriptionRepository;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private UserService userService;

    private PlannerSubscriptionService subscriptionService;

    private User testUser;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        subscriptionService = new PlannerSubscriptionService(
                subscriptionRepository, new PlannerAccessGuard(new UsersApiAdapter(userService, null), plannerRepository));

        testUser = TestDataFactory.unsavedUser(1L);

        plannerId = UUID.randomUUID();
    }

    @Nested
    class ToggleSubscriptionTests {

        @Test
        void toggleSubscription_WhenNotSubscribed_CreatesNewSubscription() {
            // Arrange
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.insert(any(PlannerSubscription.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerSubscription result = subscriptionService.toggleSubscription(testUser.getId(), plannerId);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEnabled());
            assertEquals(testUser.getId(), result.getUserId());
            assertEquals(plannerId, result.getPlannerId());

            ArgumentCaptor<PlannerSubscription> captor = ArgumentCaptor.forClass(PlannerSubscription.class);
            verify(subscriptionRepository).insert(captor.capture());
            assertTrue(captor.getValue().isEnabled());
        }

        @Test
        void toggleSubscription_WhenSubscribed_TogglesEnabled() {
            // Arrange
            PlannerSubscription existingSubscription = new PlannerSubscription(testUser.getId(), plannerId);
            // existingSubscription is enabled by default

            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(existingSubscription));

            // Act
            PlannerSubscription result = subscriptionService.toggleSubscription(testUser.getId(), plannerId);

            // Assert
            assertNotNull(result);
            assertFalse(result.isEnabled()); // Was enabled, now disabled
            assertSame(existingSubscription, result);
        }

        @Test
        void toggleSubscription_WhenDisabled_TogglesBackToEnabled() {
            // Arrange
            PlannerSubscription disabledSubscription = new PlannerSubscription(testUser.getId(), plannerId);
            disabledSubscription.toggle(); // Now disabled

            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(disabledSubscription));

            // Act
            PlannerSubscription result = subscriptionService.toggleSubscription(testUser.getId(), plannerId);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEnabled()); // Was disabled, now enabled
        }

        @Test
        void toggleSubscription_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.existsPublishedById(nonExistentId)).thenReturn(false);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> subscriptionService.toggleSubscription(testUser.getId(), nonExistentId)
            );

            verify(subscriptionRepository, never()).findByUserIdAndPlannerId(any(), any());
            verify(subscriptionRepository, never()).insert(any());
        }

        @Test
        void toggleSubscription_WhenPlannerNotPublished_ThrowsException() {
            // Arrange
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(false);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> subscriptionService.toggleSubscription(testUser.getId(), plannerId)
            );
        }
    }

    @Nested
    class IsSubscribedTests {

        @Test
        void isSubscribed_WhenSubscribedAndEnabled_ReturnsTrue() {
            // Arrange
            PlannerSubscription enabledSubscription = new PlannerSubscription(testUser.getId(), plannerId);

            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(enabledSubscription));

            // Act
            boolean result = subscriptionService.isSubscribed(testUser.getId(), plannerId);

            // Assert
            assertTrue(result);
        }

        @Test
        void isSubscribed_WhenSubscribedButDisabled_ReturnsFalse() {
            // Arrange
            PlannerSubscription disabledSubscription = new PlannerSubscription(testUser.getId(), plannerId);
            disabledSubscription.toggle(); // Now disabled

            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(disabledSubscription));

            // Act
            boolean result = subscriptionService.isSubscribed(testUser.getId(), plannerId);

            // Assert
            assertFalse(result);
        }

        @Test
        void isSubscribed_WhenNotSubscribed_ReturnsFalse() {
            // Arrange
            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.empty());

            // Act
            boolean result = subscriptionService.isSubscribed(testUser.getId(), plannerId);

            // Assert
            assertFalse(result);
        }
    }

    @Nested
    class CreateSubscriptionTests {

        @Test
        void createSubscription_WhenNotExists_CreatesSubscription() {
            // Arrange
            when(subscriptionRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);

            // Act
            subscriptionService.createSubscription(testUser.getId(), plannerId);

            // Assert
            ArgumentCaptor<PlannerSubscription> captor = ArgumentCaptor.forClass(PlannerSubscription.class);
            verify(subscriptionRepository).insert(captor.capture());

            PlannerSubscription saved = captor.getValue();
            assertEquals(testUser.getId(), saved.getUserId());
            assertEquals(plannerId, saved.getPlannerId());
            assertTrue(saved.isEnabled());
        }

        @Test
        void createSubscription_WhenExists_DoesNothing() {
            // Arrange
            when(subscriptionRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(true);

            // Act
            subscriptionService.createSubscription(testUser.getId(), plannerId);

            // Assert
            verify(subscriptionRepository, never()).insert(any());
        }
    }

    @Nested
    class GetSubscriberUserIdsTests {

        @Test
        void getSubscriberUserIds_WhenCalled_ReturnsOnlyEnabled() {
            // Arrange
            PlannerSubscription sub1 = new PlannerSubscription(1L, plannerId);
            PlannerSubscription sub2 = new PlannerSubscription(2L, plannerId);
            PlannerSubscription sub3 = new PlannerSubscription(3L, plannerId);

            when(subscriptionRepository.findByPlannerIdAndEnabledTrue(plannerId))
                    .thenReturn(List.of(sub1, sub2, sub3));

            // Act
            List<Long> result = subscriptionService.getSubscriberUserIds(plannerId);

            // Assert
            assertEquals(3, result.size());
            assertTrue(result.contains(1L));
            assertTrue(result.contains(2L));
            assertTrue(result.contains(3L));
        }

        @Test
        void getSubscriberUserIds_WhenNoSubscribers_ReturnsEmpty() {
            // Arrange
            when(subscriptionRepository.findByPlannerIdAndEnabledTrue(plannerId))
                    .thenReturn(List.of());

            // Act
            List<Long> result = subscriptionService.getSubscriberUserIds(plannerId);

            // Assert
            assertTrue(result.isEmpty());
        }
    }
}
