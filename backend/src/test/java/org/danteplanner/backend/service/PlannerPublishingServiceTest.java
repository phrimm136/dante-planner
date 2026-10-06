package org.danteplanner.backend.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator;


import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.danteplanner.backend.support.Rejections.assertRejected;

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
    private PlannerCommandService plannerCommandService;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    @Mock
    private PlannerSubscriptionService subscriptionService;

    @Mock
    private DomainEventRecorder domainEventRecorder;

    private final FakeUsers users = new FakeUsers();

    private PlannerPublishingService publishingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        publishingService = publishingServiceWith(TestDataFactory.contentValidator());

        testUser = users.add(TestDataFactory.unsavedUser(1L));
    }

    private PlannerPublishingService publishingServiceWith(PlannerContentValidator contentValidator) {
        return new PlannerPublishingService(
                plannerRepository,
                plannerStatsRepository,
                plannerCommandService,
                contentValidator,
                plannerCatalogService,
                subscriptionService,
                new PlannerAccessGuard(users, plannerRepository),
                new PlannerOwnershipValidator(),
                new PlannerPublishValidator(),
                new PlannerCategoryValidator(),
                domainEventRecorder
        );
    }

    private TestDataFactory.PlannerBuilder testPlannerBuilder() {
        return TestDataFactory.planner(testUser);
    }

    private Planner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    class PublishIntentTests {

        @Test
        void publishIdempotentStateTargeted_WhenSentTwice_StaysPublished() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse first = publishingService.publish(testUser.getId(), planner.getId());
            PlannerResponse second = publishingService.publish(testUser.getId(), planner.getId());

            assertTrue(first.published());
            assertTrue(second.published(), "a repeated publish must not flip the planner back");
            verify(plannerCatalogService, times(1)).onBecameVisible(any());
        }

        @Test
        void publishIdempotentStateTargeted_WhenUnpublishRepeated_StaysUnpublished() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            assertFalse(result.published());
            verify(plannerCatalogService, never()).onBecameInvisible(any());
        }
    }

    @Nested
    class PublishNormalizationTests {

        private final ObjectMapper mapper = new ObjectMapper();

        @TempDir
        private Path dataDir;

        private PlannerContentValidator validatorRenaming(String idMigrations) throws IOException {
            try (var entries = Files.list(Path.of("../static/data"))) {
                for (Path entry : entries.toList()) {
                    Files.createSymbolicLink(dataDir.resolve(entry.getFileName()), entry.toAbsolutePath());
                }
            }
            Files.writeString(dataDir.resolve(GameDataRegistry.ID_MIGRATIONS_FILE), idMigrations);
            GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(mapper), dataDir.toString());
            registry.init();
            return new PlannerContentValidator(mapper, 51200, 1024, registry, new SinnerIdValidator(),
                    new FloorRules(registry));
        }

        @Test
        void publish_WhenStoredContentHoldsARenamedGift_StoresTheNewId() throws Exception {
            PlannerContentValidator renaming = validatorRenaming("{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}");
            String stored = TestDataFactory.VALID_CONTENT.replace(
                    "\"observationGiftIds\":[]", "\"observationGiftIds\":[\"19247\"]");
            Planner planner = testPlannerBuilder().published(false).content(stored).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            List<String> indexedContent = new ArrayList<>();
            doAnswer(invocation -> indexedContent.add(((Planner) invocation.getArgument(0)).getContentJson()))
                    .when(plannerCatalogService).onBecameVisible(any());

            publishingServiceWith(renaming).publish(testUser.getId(), planner.getId());

            JsonNode published = mapper.readTree(planner.getContentJson());
            assertEquals(mapper.createArrayNode().add("19002"), published.path("observationGiftIds"));
            assertEquals(List.of(planner.getContentJson()), indexedContent);
            assertTrue(planner.isPublished());
        }
    }

    @Nested
    class PublishEffectRecordingTests {

        @Test
        void applyPublish_WhenFirstPublish_RecordsTheEventInsteadOfAnnouncing() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            publishingService.publish(testUser.getId(), planner.getId());

            verify(domainEventRecorder).recordDomainEvent(
                    DomainEventType.PLANNER_PUBLISHED, planner.getId(),
                    Map.of("authorId", testUser.getId()));
            verifyNoMoreInteractions(domainEventRecorder);
        }

        @Test
        void applyPublish_WhenAlreadyPublished_RecordsNothing() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            publishingService.publish(testUser.getId(), planner.getId());
            reset(domainEventRecorder);

            publishingService.publish(testUser.getId(), planner.getId());

            verifyNoInteractions(domainEventRecorder);
        }

        @Test
        void applyUnpublish_WhenWithdrawn_RecordsNothing() {
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            publishingService.unpublish(testUser.getId(), planner.getId());

            verifyNoInteractions(domainEventRecorder);
        }
    }

    @Nested
    class WithdrawFromPublicViewTests {

        @Test
        void withdrawFromPublicView_WhenAlreadyUnpublished_HasNoSideEffects() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            Planner result = publishingService.withdrawFromPublicView(
                    planner.getId(), Planner::unpublish);

            assertSame(planner, result);
            verify(plannerCatalogService, never()).onBecameInvisible(any());
        }

        @Test
        void withdrawFromPublicView_WhenPublished_PersistsAndDropsCatalogRow() {
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            Planner result = publishingService.withdrawFromPublicView(
                    planner.getId(), Planner::unpublish);

            assertSame(planner, result);
            assertFalse(result.isPublished());
            verify(plannerCatalogService).onBecameInvisible(planner.getId());
        }

        @Test
        void withdrawFromPublicView_WhenTakedownOfUnpublished_StillPersists() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            Planner result = publishingService.withdrawFromPublicView(planner.getId(), Planner::takeDown);

            assertSame(planner, result);
            assertTrue(result.isTakenDown());
        }
    }

    @Nested
    class PublishWithContentTests {

        @Test
        void publishLoadsAggregateOnce_WhenContentCarried_ReusesUpsertedAggregate() {
            Planner planner = testPlannerBuilder().published(false).build();
            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    planner.getId().toString(), "5F", "Reused Aggregate", null, "{}", 1,
                    PlannerType.MIRROR_DUNGEON, null, null);

            UUID deviceId = UUID.randomUUID();
            when(plannerCommandService.upsertAggregate(
                    testUser.getId(), deviceId, planner.getId(), request, false))
                    .thenReturn(new PlannerCommandService.UpsertedPlanner(
                            planner, PlannerResponse.fromEntity(planner), false));
            // Decoys: a reload by either route would succeed and stay silent, so the prohibitions
            // below fail on the reload itself rather than on a null downstream.
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse result = publishingService.publish(
                    testUser.getId(), deviceId, planner.getId(), request);

            assertTrue(result.published());
            verify(plannerRepository, never()).findAggregateForOwner(any(), any());
            verify(plannerRepository, never()).findAggregate(any());
        }
    }

    @Nested
    class PublicationStateTransitionTests {

        @Test
        void publish_WhenOwner_Publishes() {
            // Arrange
            Planner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act
            PlannerResponse result = publishingService.publish(testUser.getId(), planner.getId());

            // Assert
            assertTrue(result.published());
            assertEquals(planner.getId(), result.id());
            assertEquals("Test Planner", result.title());
        }

        @Test
        void unpublish_WhenPublished_Unpublishes() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act
            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            // Assert
            assertFalse(result.published());
        }

        @Test
        void publish_WhenNotOwner_ThrowsException() {
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
            planner.setUserId(otherUser.getId());

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            PlannerForbiddenException exception = assertThrows(
                    PlannerForbiddenException.class,
                    () -> publishingService.publish(testUser.getId(), planner.getId())
            );

            assertEquals(planner.getId(), exception.getPlannerId());
            assertFalse(planner.isPublished());
        }

        @Test
        void publish_WhenNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findAggregate(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> publishingService.publish(testUser.getId(), nonExistentId))
                    .withCode("PLANNER_NOT_FOUND");
        }

        @Test
        void publish_WhenDeleted_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.softDelete();

            // findAggregate filters soft-deleted rows at the query level
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> publishingService.publish(testUser.getId(), planner.getId()))
                    .withCode("PLANNER_NOT_FOUND");
        }

        @Test
        void publish_WhenPublishing_AutoSubscribesOwner() {
            // Arrange
            Planner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act
            PlannerResponse result = publishingService.publish(testUser.getId(), planner.getId());

            // Assert
            assertTrue(result.published());
            // The subscription is a row in another aggregate and never reaches this response;
            // asserting it as state needs a containerized test with a real subscription repository.
            verify(subscriptionService).createSubscription(testUser.getId(), planner.getId());
        }

        @Test
        void unpublish_WhenUnpublishing_DoesNotAutoSubscribe() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act
            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            // Assert
            assertFalse(result.published());
            verify(subscriptionService, never()).createSubscription(any(), any());
        }
    }

    @Nested
    class BanEnforcementTests {

        @Test
        void publish_WhenBannedUser_ThrowsUserBannedException() {
            // Arrange
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);

            UUID plannerId = UUID.randomUUID();

            // Act & Assert
            assertRejected(() -> publishingService.publish(testUser.getId(), plannerId)).withCode("USER_BANNED");
            verify(plannerCatalogService, never()).onBecameVisible(any());
        }

        @Test
        void unpublish_WhenBannedOwner_Unpublishes() {
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            assertFalse(result.published());
            verify(plannerCatalogService).onBecameInvisible(planner.getId());
        }
    }

    @Nested
    class ToggleOwnerNotificationsTests {

        @Test
        void toggleOwnerNotifications_WhenOwner_UpdatesSetting() {
            // Arrange
            Planner planner = createTestPlanner();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act
            ToggleOwnerNotificationsResponse response =
                    publishingService.toggleOwnerNotifications(testUser.getId(), planner.getId(), false);

            // Assert
            assertFalse(response.ownerNotificationsEnabled());
            assertFalse(planner.isOwnerNotificationsEnabled());
        }

        @Test
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
            planner.setUserId(otherUser.getId());

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            assertRejected(() -> publishingService.toggleOwnerNotifications(testUser.getId(), planner.getId(), true))
                    .withCode("PLANNER_FORBIDDEN");
            assertTrue(planner.isOwnerNotificationsEnabled());
        }
    }

    @Nested
    class PublishValidatesBeforeMutatingTests {

        @Test
        void publishValidatesBeforeMutating_WhenTitleBlank_LeavesAggregateUnpublished() {
            Planner planner = testPlannerBuilder().title("   ").published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerValidationException thrown = assertThrows(
                    PlannerValidationException.class,
                    () -> publishingService.publish(testUser.getId(), planner.getId()));

            assertEquals("MISSING_TITLE", thrown.getOriginalCode());
            assertFalse(planner.isPublished());
            assertNull(planner.getFirstPublishedAt());
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleBlankAndVersionUnknown_RefusesTitleWithoutWrite() {
            Planner planner = testPlannerBuilder().title("Stored Title").published(false).build();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    planner.getId().toString(), "5F", "  ", null, "{}", 99,
                    PlannerType.MIRROR_DUNGEON, null, null);

            PlannerValidationException thrown = assertThrows(
                    PlannerValidationException.class,
                    () -> publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.getId(), request));

            assertEquals("MISSING_TITLE", thrown.getOriginalCode());
            verifyNoInteractions(plannerCommandService);
            assertFalse(planner.isPublished());
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleAbsentAndStoredBlank_RefusesTitleWithoutWrite() {
            Planner planner = testPlannerBuilder().title("   ").published(false).build();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    planner.getId().toString(), "5F", null, null, "{}", 99,
                    PlannerType.MIRROR_DUNGEON, null, null);

            PlannerValidationException thrown = assertThrows(
                    PlannerValidationException.class,
                    () -> publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.getId(), request));

            assertEquals("MISSING_TITLE", thrown.getOriginalCode());
            verifyNoInteractions(plannerCommandService);
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleAbsentAndStoredPresent_Publishes() {
            Planner planner = testPlannerBuilder().title("Stored Title").published(false).build();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    planner.getId().toString(), "5F", null, null, "{}", 1,
                    PlannerType.MIRROR_DUNGEON, null, null);
            UUID deviceId = UUID.randomUUID();
            when(plannerCommandService.upsertAggregate(
                    testUser.getId(), deviceId, planner.getId(), request, false))
                    .thenReturn(new PlannerCommandService.UpsertedPlanner(
                            planner, PlannerResponse.fromEntity(planner), false));

            PlannerResponse result = publishingService.publish(
                    testUser.getId(), deviceId, planner.getId(), request);

            assertTrue(result.published());
        }

        @Test
        void publishValidatesBeforeMutating_WhenContentRefused_LeavesAggregateUnpublished() {
            Planner planner = testPlannerBuilder().content("").published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            assertRejected(() -> publishingService.publish(testUser.getId(), planner.getId()))
                    .withCode("EMPTY_CONTENT");

            assertFalse(planner.isPublished());
            assertNull(planner.getFirstPublishedAt());
            verify(plannerCatalogService, never()).onBecameVisible(any());
        }
    }
}
