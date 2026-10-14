package org.danteplanner.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.moderation.validation.AccountStandingValidator;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.entity.DomainEventType;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerCatalogService;
import org.danteplanner.backend.planner.service.PlannerSubscriptionService;
import org.danteplanner.backend.planner.service.PlannerCommandService;
import org.danteplanner.backend.planner.service.PlannerPublishingService;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerPublishValidator;


import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ToggleOwnerNotificationsResponse;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.entity.PlannerSubscription;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.UpsertRequests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Unit tests for PlannerPublishingService (publish toggle, owner notification settings).
 */
@ExtendWith(MockitoExtension.class)
class PlannerPublishingServiceTest {

    private static final int MAX_PLANNERS_PER_USER = 100;

    private static final int RECOMMENDED_THRESHOLD = 10;

    private static final int CURRENT_SCHEMA_VERSION = 2;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerSubscriptionRepository subscriptionRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    @Mock
    private DomainEventRepository catalogEventRepository;

    private final FakeUsers users = new FakeUsers();

    private PlannerPublishingService publishingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();

        publishingService = publishingServiceWith(TestDataFactory.contentValidator());

        testUser = users.add(TestDataFactory.unsavedUser(1L));
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void publishedEventsPersist() {
        when(domainEventRepository.insert(any(DomainEvent.class))).thenAnswer(invocation -> {
            DomainEvent row = invocation.getArgument(0);
            ReflectionTestUtils.setField(row, "id", 1L);
            return row;
        });
    }

    private PlannerPublishingService publishingServiceWith(PlannerContentValidator contentValidator) {
        ObjectMapper objectMapper = new ObjectMapper();
        PlannerAccessGuard accessGuard = new PlannerAccessGuard(users, plannerRepository);
        RestrictionCheckService restrictionCheck = new RestrictionCheckService(users, new AccountStandingValidator());
        PlannerCatalogService catalogService = new PlannerCatalogService(catalogRepository, plannerRepository,
                plannerStatsRepository, new DomainEventRecorder(catalogEventRepository, objectMapper, null),
                objectMapper, new RecordingTombstoneStore(), RECOMMENDED_THRESHOLD);
        PlannerCommandService commandService = new PlannerCommandService(
                plannerRepository,
                plannerStatsRepository,
                contentValidator,
                new ContentVersionValidator(TestDataFactory.gameDataRegistry()),
                catalogService,
                accessGuard,
                restrictionCheck,
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                objectMapper,
                MAX_PLANNERS_PER_USER,
                CURRENT_SCHEMA_VERSION);
        return new PlannerPublishingService(
                plannerRepository,
                plannerStatsRepository,
                commandService,
                contentValidator,
                catalogService,
                new PlannerSubscriptionService(subscriptionRepository, accessGuard, restrictionCheck),
                accessGuard,
                new PlannerOwnershipValidator(),
                new PlannerPublishValidator(),
                new PlannerCategoryValidator(),
                new DomainEventRecorder(domainEventRepository, new ObjectMapper(), event -> { })
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
            publishedEventsPersist();

            PlannerResponse first = publishingService.publish(testUser.getId(), planner.getId());
            PlannerResponse second = publishingService.publish(testUser.getId(), planner.getId());

            assertThat(first.published()).isTrue();
            assertThat(second.published()).as("a repeated publish must not flip the planner back").isTrue();
            verify(catalogRepository, times(1)).insert(any(PlannerCatalog.class));
        }

        @Test
        void publishIdempotentStateTargeted_WhenUnpublishRepeated_StaysUnpublished() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            assertThat(result.published()).isFalse();
            verify(catalogRepository, never()).deleteByPlannerId(any());
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
            publishedEventsPersist();
            List<String> indexedContent = new ArrayList<>();
            doAnswer(invocation -> {
                indexedContent.add(planner.getContentJson());
                return null;
            }).when(catalogRepository).insert(any(PlannerCatalog.class));

            publishingServiceWith(renaming).publish(testUser.getId(), planner.getId());

            JsonNode published = mapper.readTree(planner.getContentJson());
            assertThat(published.path("observationGiftIds")).isEqualTo(mapper.createArrayNode().add("19002"));
            assertThat(indexedContent).isEqualTo(List.of(planner.getContentJson()));
            assertThat(planner.isPublished()).isTrue();
        }
    }

    @Nested
    class PublishEffectRecordingTests {

        @Test
        void applyPublish_WhenFirstPublish_RecordsTheEventInsteadOfAnnouncing() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            publishedEventsPersist();

            publishingService.publish(testUser.getId(), planner.getId());

            ArgumentCaptor<DomainEvent> recorded = ArgumentCaptor.forClass(DomainEvent.class);
            verify(domainEventRepository).insert(recorded.capture());
            verifyNoMoreInteractions(domainEventRepository);
            assertThat(recorded.getValue().getEventType()).isEqualTo(DomainEventType.PLANNER_PUBLISHED);
            assertThat(recorded.getValue().getAggregateId()).isEqualTo(planner.getId());
            assertThat(recorded.getValue().getPayload()).isEqualTo("{\"authorId\":" + testUser.getId() + "}");
        }

        @Test
        void applyPublish_WhenAlreadyPublished_RecordsNothing() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            publishedEventsPersist();
            publishingService.publish(testUser.getId(), planner.getId());
            clearInvocations(domainEventRepository);

            publishingService.publish(testUser.getId(), planner.getId());

            verify(domainEventRepository, never()).insert(any());
        }

        @Test
        void applyUnpublish_WhenWithdrawn_RecordsNothing() {
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            publishingService.unpublish(testUser.getId(), planner.getId());

            verify(domainEventRepository, never()).insert(any());
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

            assertThat(result).isSameAs(planner);
            verify(catalogRepository, never()).deleteByPlannerId(any());
        }

        @Test
        void withdrawFromPublicView_WhenPublished_PersistsAndDropsCatalogRow() {
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            Planner result = publishingService.withdrawFromPublicView(
                    planner.getId(), Planner::unpublish);

            assertThat(result).isSameAs(planner);
            assertThat(result.isPublished()).isFalse();
            verify(catalogRepository).deleteByPlannerId(planner.getId());
        }

        @Test
        void withdrawFromPublicView_WhenTakedownOfUnpublished_StillPersists() {
            Planner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            Planner result = publishingService.withdrawFromPublicView(planner.getId(), Planner::takeDown);

            assertThat(result).isSameAs(planner);
            assertThat(result.isTakenDown()).isTrue();
        }
    }

    @Nested
    class PublishWithContentTests {

        @Test
        void publishLoadsAggregateOnce_WhenContentCarried_ReusesUpsertedAggregate() {
            Planner planner = testPlannerBuilder().published(false).build();
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title("Reused Aggregate").build();

            UUID deviceId = UUID.randomUUID();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            publishedEventsPersist();

            PlannerResponse result = publishingService.publish(
                    testUser.getId(), deviceId, planner.getId(), request);

            assertThat(result.published()).isTrue();
            assertThat(result.title()).isEqualTo("Reused Aggregate");
            verify(plannerRepository, times(1)).findAggregateForOwner(any(), any());
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
            publishedEventsPersist();

            // Act
            PlannerResponse result = publishingService.publish(testUser.getId(), planner.getId());

            // Assert
            assertThat(result.published()).isTrue();
            assertThat(result.id()).isEqualTo(planner.getId());
            assertThat(result.title()).isEqualTo("Test Planner");
        }

        @Test
        void unpublish_WhenPublished_Unpublishes() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act
            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            // Assert
            assertThat(result.published()).isFalse();
        }

        @Test
        void publish_WhenNotOwner_ThrowsException() {
            // Arrange
            User otherUser = TestDataFactory.unsavedUser(999L);
            Planner planner = createTestPlanner();
            planner.setUserId(otherUser.getId());

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            assertRejected(() -> publishingService.publish(testUser.getId(), planner.getId()))
                    .withCode("PLANNER_FORBIDDEN")
                    .isInstanceOfSatisfying(PlannerForbiddenException.class,
                            exception -> assertThat(exception.getPlannerId()).isEqualTo(planner.getId()));
            assertThat(planner.isPublished()).isFalse();
        }

        @Test
        void publish_WhenPlannerMissingOrDeleted_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findAggregate(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> publishingService.publish(testUser.getId(), nonExistentId))
                    .withCode("PLANNER_NOT_FOUND");
        }

        @Test
        void publish_WhenPublishing_AutoSubscribesOwner() {
            // Arrange
            Planner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));
            publishedEventsPersist();

            // Act
            PlannerResponse result = publishingService.publish(testUser.getId(), planner.getId());

            // Assert
            assertThat(result.published()).isTrue();
            // The subscription is a row in another aggregate and never reaches this response;
            // asserting it as state needs a containerized test with a real subscription repository.
            ArgumentCaptor<PlannerSubscription> subscription = ArgumentCaptor.forClass(PlannerSubscription.class);
            verify(subscriptionRepository).insert(subscription.capture());
            assertThat(subscription.getValue().getUserId()).isEqualTo(testUser.getId());
            assertThat(subscription.getValue().getPlannerId()).isEqualTo(planner.getId());
        }

        @Test
        void unpublish_WhenUnpublishing_DoesNotAutoSubscribe() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act
            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            // Assert
            assertThat(result.published()).isFalse();
            verify(subscriptionRepository, never()).insert(any());
        }
    }

    @Nested
    class BanEnforcementTests {

        @Test
        void unpublish_WhenBannedOwner_Unpublishes() {
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);
            Planner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.getId());

            assertThat(result.published()).isFalse();
            verify(catalogRepository).deleteByPlannerId(planner.getId());
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
            assertThat(response.ownerNotificationsEnabled()).isFalse();
            assertThat(planner.isOwnerNotificationsEnabled()).isFalse();
        }

        @Test
        void toggleOwnerNotifications_WhenNotOwner_ThrowsException() {
            // Arrange
            User otherUser = TestDataFactory.unsavedUser(999L);
            Planner planner = createTestPlanner();
            planner.setUserId(otherUser.getId());

            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            assertRejected(() -> publishingService.toggleOwnerNotifications(testUser.getId(), planner.getId(), true))
                    .withCode("PLANNER_FORBIDDEN");
            assertThat(planner.isOwnerNotificationsEnabled()).isTrue();
        }
    }

    @Nested
    class PublishValidatesBeforeMutatingTests {

        @Test
        void publishValidatesBeforeMutating_WhenTitleBlank_LeavesAggregateUnpublished() {
            Planner planner = testPlannerBuilder().title("   ").published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            assertRejected(() -> publishingService.publish(testUser.getId(), planner.getId()))
                    .withViolation("MISSING_TITLE")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            thrown -> assertThat(thrown.getOriginalCode()).isEqualTo("MISSING_TITLE"));
            assertThat(planner.isPublished()).isFalse();
            assertThat(planner.getFirstPublishedAt()).isNull();
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleBlankAndVersionUnknown_RefusesTitleWithoutWrite() {
            Planner planner = testPlannerBuilder().title("Stored Title").published(false).build();
            long syncVersionBefore = planner.getSyncVersion();
            lenient().when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .id(planner.getId().toString())
                    .title("  ")
                    .status(null)
                    .content("{}")
                    .contentVersion(99)
                    .build();

            assertRejected(() -> publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.getId(),
                    request))
                    .withViolation("MISSING_TITLE")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            thrown -> assertThat(thrown.getOriginalCode()).isEqualTo("MISSING_TITLE"));
            assertThat(planner.getSyncVersion()).isEqualTo(syncVersionBefore);
            assertThat(planner.getTitle()).isEqualTo("Stored Title");
            assertThat(planner.isPublished()).isFalse();
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleAbsentAndStoredBlank_RefusesTitleWithoutWrite() {
            Planner planner = testPlannerBuilder().title("   ").published(false).build();
            long syncVersionBefore = planner.getSyncVersion();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .id(planner.getId().toString())
                    .title(null)
                    .status(null)
                    .content("{}")
                    .contentVersion(99)
                    .build();

            assertRejected(() -> publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.getId(),
                    request))
                    .withViolation("MISSING_TITLE")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            thrown -> assertThat(thrown.getOriginalCode()).isEqualTo("MISSING_TITLE"));
            assertThat(planner.getSyncVersion()).isEqualTo(syncVersionBefore);
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleAbsentAndStoredPresent_Publishes() {
            Planner planner = testPlannerBuilder().title("Stored Title").published(false).build();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title(null).build();
            UUID deviceId = UUID.randomUUID();
            publishedEventsPersist();

            PlannerResponse result = publishingService.publish(
                    testUser.getId(), deviceId, planner.getId(), request);

            assertThat(result.published()).isTrue();
            assertThat(result.title()).isEqualTo("Stored Title");
        }

        @Test
        void publishValidatesBeforeMutating_WhenContentRefused_LeavesAggregateUnpublished() {
            Planner planner = testPlannerBuilder().content("").published(false).build();
            when(plannerRepository.findAggregate(planner.getId())).thenReturn(Optional.of(planner));

            assertRejected(() -> publishingService.publish(testUser.getId(), planner.getId()))
                    .withCode("EMPTY_CONTENT");

            assertThat(planner.isPublished()).isFalse();
            assertThat(planner.getFirstPublishedAt()).isNull();
            verify(catalogRepository, never()).insert(any());
        }
    }
}
