package org.danteplanner.backend.service;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;

import org.danteplanner.backend.notification.service.NotificationDispatchService;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.service.UserService;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.danteplanner.backend.user.exception.UserBannedException;

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
    private UserService userService;

    @Mock
    private PlannerContentValidator contentValidator;

    @Mock
    private PlannerCommandService plannerCommandService;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    @Mock
    private PlannerSubscriptionService subscriptionService;

    @Mock
    private SsePublisher ssePublisher;

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private PlannerPublishingService publishingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlannerAccessGuard accessGuard = new PlannerAccessGuard(userService, plannerRepository);

        publishingService = new PlannerPublishingService(
                plannerRepository,
                plannerStatsRepository,
                plannerCommandService,
                contentValidator,
                plannerCatalogService,
                subscriptionService,
                ssePublisher,
                notificationDispatchService,
                accessGuard,
                eventPublisher
        );

        testUser = TestDataFactory.unsavedUser(1L);

        when(userService.findById(testUser.getId())).thenReturn(testUser);
    }

    private TestDataFactory.PlannerBuilder testPlannerBuilder() {
        return TestDataFactory.planner(testUser);
    }

    private Planner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    @DisplayName("setPublished Tests")
    class SetPublishedTests {

        @Test
        @DisplayName("publishing an already-published planner twice leaves it published")
        void publishIdempotentStateTargeted_WhenSentTwice_StaysPublished() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PlannerResponse first = publishingService.setPublished(testUser.getId(), planner.getId(), true);
            PlannerResponse second = publishingService.setPublished(testUser.getId(), planner.getId(), true);

            assertTrue(first.published());
            assertTrue(second.published(), "a repeated publish must not flip the planner back");
            verify(plannerRepository, times(1)).save(any(Planner.class));
        }

        @Test
        @DisplayName("unpublishing an already-unpublished planner leaves it unpublished")
        void publishIdempotentStateTargeted_WhenUnpublishRepeated_StaysUnpublished() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse result = publishingService.setPublished(testUser.getId(), planner.getId(), false);

            assertFalse(result.published());
            verify(plannerRepository, never()).save(any(Planner.class));
        }
    }

    @Nested
    @DisplayName("withdrawFromPublicView Tests")
    class WithdrawFromPublicViewTests {

        @Test
        @DisplayName("withdrawing an already-unpublished planner writes nothing and drops no catalog row")
        void withdrawFromPublicView_WhenAlreadyUnpublished_HasNoSideEffects() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            Planner result = publishingService.withdrawFromPublicView(planner.getId(), Planner::unpublish);

            assertSame(planner, result);
            verify(plannerRepository, never()).save(any(Planner.class));
            verify(plannerCatalogService, never()).onBecameInvisible(any());
        }

        @Test
        @DisplayName("withdrawing a published planner persists it and drops its catalog row")
        void withdrawFromPublicView_WhenPublished_PersistsAndDropsCatalogRow() {
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Planner result = publishingService.withdrawFromPublicView(planner.getId(), Planner::unpublish);

            assertFalse(result.getPublished());
            verify(plannerRepository).save(planner);
            verify(plannerCatalogService).onBecameInvisible(planner.getId());
        }

        @Test
        @DisplayName("taking down an unpublished planner still persists the moderation stamp")
        void withdrawFromPublicView_WhenTakedownOfUnpublished_StillPersists() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Planner result = publishingService.withdrawFromPublicView(planner.getId(), Planner::takeDown);

            assertTrue(result.isTakenDown());
            verify(plannerRepository).save(planner);
        }
    }

    @Nested
    @DisplayName("publishWithContent Tests")
    class PublishWithContentTests {

        @Test
        @DisplayName("publish-with-content reuses the aggregate the upsert already loaded")
        void publishLoadsAggregateOnce_WhenContentCarried_ReusesUpsertedAggregate() {
            Planner planner = testPlannerBuilder().published(false).build();
            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    planner.getId().toString(), "5F", "Reused Aggregate", null, "{}", 1,
                    PlannerType.MIRROR_DUNGEON, null, null);

            when(plannerCommandService.upsertAggregate(
                    testUser.getId(), null, planner.getId(), request, false))
                    .thenReturn(new PlannerCommandService.UpsertedPlanner(
                            planner, PlannerResponse.fromEntity(planner, 0), false));
            // Decoys: a reload by either route would succeed and stay silent, so the prohibitions
            // below fail on the reload itself rather than on a null downstream.
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(planner))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            PlannerResponse result = publishingService.setPublishedWithContent(
                    testUser.getId(), planner.getId(), request, true);

            assertTrue(result.published());
            verify(plannerRepository, never()).findAggregateForOwner(any(), any());
            verify(plannerRepository, never()).findAggregate(any());
        }
    }

    @Nested
    @DisplayName("publication state transition Tests")
    class PublicationStateTransitionTests {

        @Test
        @DisplayName("Should publish when owner")
        void setPublished_WhenOwner_Publishes() {
            // Arrange
            Planner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(planner)).thenAnswer(invocation -> invocation.getArgument(0));
            // An upvote count no other source could supply, so the response can only carry it by
            // having read this planner's own stats row.
            when(plannerStatsRepository.upvotesOf(planner.getId())).thenReturn(12);

            // Act
            PlannerResponse result = publishingService.setPublished(testUser.getId(), planner.getId(), true);

            // Assert
            assertTrue(result.published());
            assertEquals(planner.getId(), result.id());
            assertEquals("Test Planner", result.title());
            assertEquals(12, result.upvotes());
        }

        @Test
        @DisplayName("Should unpublish a published planner")
        void setPublished_WhenPublished_Unpublishes() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse result = publishingService.setPublished(testUser.getId(), planner.getId(), false);

            // Assert
            assertFalse(result.published());
        }

        @Test
        @DisplayName("Should throw PlannerForbiddenException when not owner")
        void setPublished_WhenNotOwner_ThrowsException() {
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
                    () -> publishingService.setPublished(testUser.getId(), planner.getId(), true)
            );

            assertEquals(planner.getId(), exception.getPlannerId());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void setPublished_WhenNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findAggregate(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishingService.setPublished(testUser.getId(), nonExistentId, true)
            );
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner is deleted")
        void setPublished_WhenDeleted_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.softDelete();

            // findAggregate filters soft-deleted rows at the query level
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishingService.setPublished(testUser.getId(), planner.getId(), true)
            );
        }

        @Test
        @DisplayName("Should auto-subscribe owner when publishing")
        void setPublished_WhenPublishing_AutoSubscribesOwner() {
            // Arrange
            Planner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse result = publishingService.setPublished(testUser.getId(), planner.getId(), true);

            // Assert
            assertTrue(result.published());
            // The subscription is a row in another aggregate and never reaches this response;
            // asserting it as state needs a containerized test with a real subscription repository.
            verify(subscriptionService).createSubscription(testUser.getId(), planner.getId());
        }

        @Test
        @DisplayName("Should not auto-subscribe when unpublishing")
        void setPublished_WhenUnpublishing_DoesNotAutoSubscribe() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse result = publishingService.setPublished(testUser.getId(), planner.getId(), false);

            // Assert
            assertFalse(result.published());
            verify(subscriptionService, never()).createSubscription(any(), any());
        }
    }

    @Nested
    @DisplayName("Ban Enforcement Tests")
    class BanEnforcementTests {

        @Test
        @DisplayName("Banned user cannot change publication state")
        void setPublished_WhenBannedUser_ThrowsUserBannedException() {
            // Arrange
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);

            UUID plannerId = UUID.randomUUID();
            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);

            // Act & Assert
            assertThrows(
                    UserBannedException.class,
                    () -> publishingService.setPublished(testUser.getId(), plannerId, true)
            );
            verify(plannerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("toggleOwnerNotifications Tests")
    class ToggleOwnerNotificationsTests {

        @Test
        @DisplayName("Should update setting when owner")
        void toggleOwnerNotifications_WhenOwner_UpdatesSetting() {
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
            // The flip is visible on the aggregate; its persistence is not. Asserting the stored
            // row needs a containerized test against a real repository.
            verify(plannerRepository).save(planner);
        }

        @Test
        @DisplayName("Should throw PlannerForbiddenException when not owner")
        void toggleOwnerNotifications_WhenNotOwner_ThrowsException() {
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
