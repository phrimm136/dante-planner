package org.danteplanner.backend.service;
import org.danteplanner.backend.shared.sse.SseService;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.service.PlannerIndexService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;

import org.danteplanner.backend.notification.service.NotificationService;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
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
    private UserRepository userRepository;

    @Mock
    private PlannerContentValidator contentValidator;

    @Mock
    private PlannerIndexService plannerIndexService;

    @Mock
    private PlannerSubscriptionService subscriptionService;

    @Mock
    private SseService notificationSseService;

    @Mock
    private NotificationService notificationService;

    private PlannerPublishingService publishingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlannerAccessGuard accessGuard = new PlannerAccessGuard(userRepository, plannerRepository);

        publishingService = new PlannerPublishingService(
                plannerRepository,
                contentValidator,
                plannerIndexService,
                subscriptionService,
                notificationSseService,
                notificationService,
                accessGuard
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

    private Planner.PlannerBuilder testPlannerBuilder() {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Test Planner")
                .category("5F")
                .status(PlannerStatus.DRAFT)
                .content("{\"data\": \"test\"}")
                .syncVersion(1L)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .savedAt(Instant.now());
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

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerRepository.saveAndFlush(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Planner result = publishingService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertTrue(result.getPublished());
            verify(plannerRepository).save(any(Planner.class));
        }

        @Test
        @DisplayName("Should toggle from published to unpublished")
        void togglePublish_WhenPublished_TogglesToUnpublished() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Planner result = publishingService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertFalse(result.getPublished());
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

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));

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
            when(plannerRepository.findById(nonExistentId)).thenReturn(Optional.empty());

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

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));

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

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerRepository.saveAndFlush(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Planner result = publishingService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertTrue(result.getPublished());
            verify(subscriptionService).createSubscription(testUser.getId(), planner.getId());
        }

        @Test
        @DisplayName("Should not auto-subscribe when unpublishing")
        void togglePublish_Unpublishing_DoesNotAutoSubscribe() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Planner result = publishingService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertFalse(result.getPublished());
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
            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
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

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            assertThrows(
                    PlannerForbiddenException.class,
                    () -> publishingService.toggleOwnerNotifications(testUser.getId(), planner.getId(), true)
            );
            verify(plannerRepository, never()).save(any());
        }
    }
}
