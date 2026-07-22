package org.danteplanner.backend.service;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerFilterService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;

import org.danteplanner.backend.notification.service.NotificationService;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlannerPublishingService (publish toggle, owner notification settings).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PlannerPublishingServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlannerContentValidator contentValidator;

    @Mock
    private PlannerFilterService plannerFilterService;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    @Mock
    private PlannerSubscriptionService subscriptionService;

    @Mock
    private SseService notificationSseService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private PlannerPublishingService publishingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlannerAccessGuard accessGuard = new PlannerAccessGuard(userRepository, plannerRepository);

        publishingService = new PlannerPublishingService(
                plannerRepository,
                plannerStatsRepository,
                contentValidator,
                plannerFilterService,
                plannerCatalogService,
                subscriptionService,
                notificationSseService,
                notificationService,
                accessGuard,
                eventPublisher
        );

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
    }

    private TestDataFactory.PlannerBuilder testPlannerBuilder() {
        return TestDataFactory.planner(testUser);
    }

    private Planner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    @DisplayName("togglePublish Tests")
    class TogglePublishTests {

        @Test
        @DisplayName("Should toggle publish status when owner")
        void togglePublish_Owner_TogglesStatus() {
            // Arrange
            Planner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse result = publishingService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertTrue(result.published());
            verify(plannerRepository).save(any(Planner.class));
        }

        @Test
        @DisplayName("Should toggle from published to unpublished")
        void togglePublish_WhenPublished_TogglesToUnpublished() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse result = publishingService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertFalse(result.published());
        }

        @Test
        @DisplayName("Should throw PlannerForbiddenException when not owner")
        void togglePublish_NotOwner_ThrowsException() {
            // Arrange
            User otherUser = User.builder()
                    .id(999L)
                    .email("other@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("google-999")
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("test2")
                    .build();
            Planner planner = createTestPlanner();
            planner.setUser(otherUser);

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            PlannerForbiddenException exception = assertThrows(
                    PlannerForbiddenException.class,
                    () -> publishingService.togglePublish(testUser.getId(), planner.getId())
            );

            assertEquals(planner.getId(), exception.getPlannerId());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void togglePublish_NotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findAggregate(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishingService.togglePublish(testUser.getId(), nonExistentId)
            );
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner is deleted")
        void togglePublish_Deleted_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.softDelete();

            // findAggregate filters soft-deleted rows at the query level
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishingService.togglePublish(testUser.getId(), planner.getId())
            );
        }

        @Test
        @DisplayName("Should auto-subscribe owner when publishing")
        void togglePublish_Publishing_AutoSubscribesOwner() {
            // Arrange
            Planner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse result = publishingService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertTrue(result.published());
            verify(subscriptionService).createSubscription(testUser.getId(), planner.getId());
        }

        @Test
        @DisplayName("Should not auto-subscribe when unpublishing")
        void togglePublish_Unpublishing_DoesNotAutoSubscribe() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse result = publishingService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertFalse(result.published());
            verify(subscriptionService, never()).createSubscription(any(), any());
        }
    }

    @Nested
    @DisplayName("Ban Enforcement Tests")
    class BanEnforcementTests {

        @Test
        @DisplayName("Banned user cannot toggle publish")
        void togglePublish_bannedUser_throwsUserBannedException() {
            // Arrange
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);

            UUID plannerId = UUID.randomUUID();
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));

            // Act & Assert
            assertThrows(
                    org.danteplanner.backend.user.exception.UserBannedException.class,
                    () -> publishingService.togglePublish(testUser.getId(), plannerId)
            );
            verify(plannerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("toggleOwnerNotifications Tests")
    class ToggleOwnerNotificationsTests {

        @Test
        @DisplayName("Should update setting when owner")
        void toggleOwnerNotifications_Owner_UpdatesSetting() {
            // Arrange
            Planner planner = createTestPlanner();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ToggleOwnerNotificationsResponse response =
                    publishingService.toggleOwnerNotifications(testUser.getId(), planner.getId(), false);

            // Assert
            assertFalse(response.ownerNotificationsEnabled());
            assertFalse(planner.getOwnerNotificationsEnabled());
            verify(plannerRepository).save(planner);
        }

        @Test
        @DisplayName("Should throw PlannerForbiddenException when not owner")
        void toggleOwnerNotifications_NotOwner_ThrowsException() {
            // Arrange
            User otherUser = User.builder()
                    .id(999L)
                    .email("other@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("google-999")
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("test2")
                    .build();
            Planner planner = createTestPlanner();
            planner.setUser(otherUser);

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            assertThrows(
                    PlannerForbiddenException.class,
                    () -> publishingService.toggleOwnerNotifications(testUser.getId(), planner.getId(), true)
            );
            verify(plannerRepository, never()).save(any());
        }
    }
}
