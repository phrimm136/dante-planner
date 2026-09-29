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


import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerDocument;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    private DomainEventRecorder domainEventRecorder;

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
                accessGuard,
                new PlannerOwnershipValidator(),
                new PlannerPublishValidator(),
                new PlannerCategoryValidator(),
                domainEventRecorder
        );

        testUser = TestDataFactory.unsavedUser(1L);

        when(userService.findById(testUser.getId())).thenReturn(testUser);
        when(contentValidator.parse(any(), any(), any(), any()))
                .thenAnswer(invocation -> TestDataFactory.document(invocation.getArgument(0)));
    }

    private TestDataFactory.PlannerBuilder testPlannerBuilder() {
        return TestDataFactory.planner(testUser);
    }

    private Planner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    @DisplayName("publish intent Tests")
    class SetPublishedTests {

        @Test
        @DisplayName("publishing an already-published planner twice leaves it published")
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
        @DisplayName("unpublishing an already-unpublished planner leaves it unpublished")
        void publishIdempotentStateTargeted_WhenUnpublishRepeated_StaysUnpublished() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            assertFalse(result.published());
            verify(plannerCatalogService, never()).onBecameInvisible(any());
        }
    }

    @Nested
    @DisplayName("publish normalization Tests")
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
        @DisplayName("publishing stored content that holds a renamed gift stores the replacement id")
        void publish_WhenStoredContentHoldsARenamedGift_StoresTheNewId() throws Exception {
            PlannerContentValidator renaming = validatorRenaming("{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}");
            String stored = TestDataFactory.VALID_CONTENT.replace(
                    "\"selectedGiftIds\":[\"9001\"]", "\"selectedGiftIds\":[\"9001\"],\"observationGiftIds\":[\"19247\"]");
            Planner planner = testPlannerBuilder().published(false).content(stored).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            doAnswer(invocation -> renaming.parse(stored, MDCategory.F5,
                    renaming.season(planner.getContentVersion()), Stage.PUBLISH))
                    .when(contentValidator).parse(eq(stored), eq(MDCategory.F5), any(), eq(Stage.PUBLISH));
            List<String> indexedContent = new ArrayList<>();
            doAnswer(invocation -> indexedContent.add(((Planner) invocation.getArgument(0)).getContentJson()))
                    .when(plannerCatalogService).onBecameVisible(any());

            publishingService.publish(testUser.getId(), planner.getId());

            JsonNode published = mapper.readTree(planner.getContentJson());
            assertEquals(mapper.createArrayNode().add("19002"), published.path("observationGiftIds"));
            assertEquals(List.of(planner.getContentJson()), indexedContent);
            assertTrue(planner.isPublished());
        }

        @Test
        @DisplayName("publishing content no migration touches leaves the stored string as it was")
        void publish_WhenNothingIsMigrated_KeepsTheStoredString() {
            Planner planner = testPlannerBuilder().published(false).build();
            String stored = planner.getContentJson();
            PlannerDocument unmigrated = TestDataFactory.document(
                    TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT, java.util.Set.of("Burst")));
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            doReturn(unmigrated)
                    .when(contentValidator).parse(eq(stored), any(), any(), eq(Stage.PUBLISH));

            publishingService.publish(testUser.getId(), planner.getId());

            assertSame(stored, planner.getContentJson());
        }
    }

    @Nested
    @DisplayName("publish effect recording Tests")
    class PublishEffectRecordingTests {

        @Test
        @DisplayName("a first publish records one PLANNER_PUBLISHED event and announces nothing itself")
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
        @DisplayName("a repeated publish records nothing, because nothing changed to announce")
        void applyPublish_WhenAlreadyPublished_RecordsNothing() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            publishingService.publish(testUser.getId(), planner.getId());
            reset(domainEventRecorder);

            publishingService.publish(testUser.getId(), planner.getId());

            verifyNoInteractions(domainEventRecorder);
        }

        @Test
        @DisplayName("a withdrawal records nothing, because it has no observer effect")
        void applyUnpublish_WhenWithdrawn_RecordsNothing() {
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            publishingService.unpublish(testUser.getId(), planner.getId());

            verifyNoInteractions(domainEventRecorder);
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

            Planner result = publishingService.withdrawFromPublicView(
                    planner.getId(), Planner::unpublish);

            assertSame(planner, result);
            verify(plannerCatalogService, never()).onBecameInvisible(any());
        }

        @Test
        @DisplayName("withdrawing a published planner persists it and drops its catalog row")
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
        @DisplayName("taking down an unpublished planner still persists the moderation stamp")
        void withdrawFromPublicView_WhenTakedownOfUnpublished_StillPersists() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            Planner result = publishingService.withdrawFromPublicView(planner.getId(), Planner::takeDown);

            assertSame(planner, result);
            assertTrue(result.isTakenDown());
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
    @DisplayName("publication state transition Tests")
    class PublicationStateTransitionTests {

        @Test
        @DisplayName("Should publish when owner")
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
        @DisplayName("Should unpublish a published planner")
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
        @DisplayName("Should throw PlannerForbiddenException when not owner")
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
            planner.setUser(otherUser);

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
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void publish_WhenNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findAggregate(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishingService.publish(testUser.getId(), nonExistentId)
            );
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner is deleted")
        void publish_WhenDeleted_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.softDelete();

            // findAggregate filters soft-deleted rows at the query level
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> publishingService.publish(testUser.getId(), planner.getId())
            );
        }

        @Test
        @DisplayName("Should auto-subscribe owner when publishing")
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
        @DisplayName("Should not auto-subscribe when unpublishing")
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
    @DisplayName("Ban Enforcement Tests")
    class BanEnforcementTests {

        @Test
        @DisplayName("Banned user cannot change publication state")
        void publish_WhenBannedUser_ThrowsUserBannedException() {
            // Arrange
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);

            UUID plannerId = UUID.randomUUID();
            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);

            // Act & Assert
            assertThrows(
                    UserBannedException.class,
                    () -> publishingService.publish(testUser.getId(), plannerId)
            );
            verify(plannerCatalogService, never()).onBecameVisible(any());
        }

        @Test
        @DisplayName("a ban withdraws distribution, so a banned owner may still unpublish")
        void unpublish_WhenBannedOwner_Unpublishes() {
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            assertFalse(result.published());
            verify(plannerCatalogService).onBecameInvisible(planner.getId());
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

            // Act
            ToggleOwnerNotificationsResponse response =
                    publishingService.toggleOwnerNotifications(testUser.getId(), planner.getId(), false);

            // Assert
            assertFalse(response.ownerNotificationsEnabled());
            assertFalse(planner.isOwnerNotificationsEnabled());
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
            assertTrue(planner.isOwnerNotificationsEnabled());
        }
    }

    @Nested
    @DisplayName("publish-validates-before-mutating")
    class PublishValidatesBeforeMutatingTests {

        @Test
        @DisplayName("a blank title refuses the publish before the aggregate moves")
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
        @DisplayName("a blank title in the body refuses the publish before an unknown version reaches the upsert")
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
        @DisplayName("an absent body title with a blank stored title refuses the publish without a write")
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
        @DisplayName("an absent body title keeps the stored title, which admits the publish")
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
        @DisplayName("refused content leaves the aggregate where it was")
        void publishValidatesBeforeMutating_WhenContentRefused_LeavesAggregateUnpublished() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            doThrow(new PlannerValidationException("EMPTY_CONTENT", "Content is required"))
                    .when(contentValidator)
                    .parse(any(), any(), any(), eq(Stage.PUBLISH));

            assertThrows(
                    PlannerValidationException.class,
                    () -> publishingService.publish(testUser.getId(), planner.getId()));

            assertFalse(planner.isPublished());
            assertNull(planner.getFirstPublishedAt());
            verify(plannerCatalogService, never()).onBecameVisible(any());
        }
    }
}
