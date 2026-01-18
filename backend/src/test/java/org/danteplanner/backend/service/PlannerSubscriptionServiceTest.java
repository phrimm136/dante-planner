package org.danteplanner.backend.service;

import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerSubscription;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.PlannerSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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

    private PlannerSubscriptionService subscriptionService;

    private User testUser;
    private Planner publishedPlanner;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        subscriptionService = new PlannerSubscriptionService(subscriptionRepository, plannerRepository);

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .usernameKeyword("W_CORP")
                .usernameSuffix("test1")
                .build();

        plannerId = UUID.randomUUID();

        publishedPlanner = Planner.builder()
                .id(plannerId)
                .user(testUser)
                .title("Published Planner")
                .category("5F")
                .status("draft")
                .content("{\"data\": \"test\"}")
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(true)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("toggleSubscription Tests")
    class ToggleSubscriptionTests {

        @Test
        @DisplayName("Should create new subscription when not subscribed")
        void toggleSubscription_WhenNotSubscribed_CreatesNewSubscription() {
            // Arrange
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.save(any(PlannerSubscription.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerSubscription result = subscriptionService.toggleSubscription(testUser.getId(), plannerId);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEnabled());
            assertEquals(testUser.getId(), result.getUserId());
            assertEquals(plannerId, result.getPlannerId());

            ArgumentCaptor<PlannerSubscription> captor = ArgumentCaptor.forClass(PlannerSubscription.class);
            verify(subscriptionRepository).save(captor.capture());
            assertTrue(captor.getValue().isEnabled());
        }

        @Test
        @DisplayName("Should toggle enabled to disabled when already subscribed")
        void toggleSubscription_WhenSubscribed_TogglesEnabled() {
            // Arrange
            PlannerSubscription existingSubscription = new PlannerSubscription(testUser.getId(), plannerId);
            // existingSubscription is enabled by default

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(existingSubscription));
            when(subscriptionRepository.save(any(PlannerSubscription.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerSubscription result = subscriptionService.toggleSubscription(testUser.getId(), plannerId);

            // Assert
            assertNotNull(result);
            assertFalse(result.isEnabled()); // Was enabled, now disabled
            verify(subscriptionRepository).save(existingSubscription);
        }

        @Test
        @DisplayName("Should toggle disabled to enabled when re-subscribing")
        void toggleSubscription_WhenDisabled_TogglesBackToEnabled() {
            // Arrange
            PlannerSubscription disabledSubscription = new PlannerSubscription(testUser.getId(), plannerId);
            disabledSubscription.toggle(); // Now disabled

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(subscriptionRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(disabledSubscription));
            when(subscriptionRepository.save(any(PlannerSubscription.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerSubscription result = subscriptionService.toggleSubscription(testUser.getId(), plannerId);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEnabled()); // Was disabled, now enabled
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void toggleSubscription_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> subscriptionService.toggleSubscription(testUser.getId(), nonExistentId)
            );

            verify(subscriptionRepository, never()).findByUserIdAndPlannerId(any(), any());
            verify(subscriptionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not published")
        void toggleSubscription_WhenPlannerNotPublished_ThrowsException() {
            // Arrange
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> subscriptionService.toggleSubscription(testUser.getId(), plannerId)
            );
        }
    }

    @Nested
    @DisplayName("isSubscribed Tests")
    class IsSubscribedTests {

        @Test
        @DisplayName("Should return true when subscribed and enabled")
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
        @DisplayName("Should return false when subscribed but disabled")
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
        @DisplayName("Should return false when not subscribed")
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
    @DisplayName("createSubscription Tests")
    class CreateSubscriptionTests {

        @Test
        @DisplayName("Should create subscription when not exists")
        void createSubscription_WhenNotExists_CreatesSubscription() {
            // Arrange
            when(subscriptionRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);

            // Act
            subscriptionService.createSubscription(testUser.getId(), plannerId);

            // Assert
            ArgumentCaptor<PlannerSubscription> captor = ArgumentCaptor.forClass(PlannerSubscription.class);
            verify(subscriptionRepository).save(captor.capture());

            PlannerSubscription saved = captor.getValue();
            assertEquals(testUser.getId(), saved.getUserId());
            assertEquals(plannerId, saved.getPlannerId());
            assertTrue(saved.isEnabled());
        }

        @Test
        @DisplayName("Should do nothing when subscription already exists")
        void createSubscription_WhenExists_DoesNothing() {
            // Arrange
            when(subscriptionRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(true);

            // Act
            subscriptionService.createSubscription(testUser.getId(), plannerId);

            // Assert
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getSubscriberUserIds Tests")
    class GetSubscriberUserIdsTests {

        @Test
        @DisplayName("Should return only enabled subscriber user IDs")
        void getSubscriberUserIds_ReturnsOnlyEnabled() {
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
        @DisplayName("Should return empty list when no subscribers")
        void getSubscriberUserIds_NoSubscribers_ReturnsEmpty() {
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
