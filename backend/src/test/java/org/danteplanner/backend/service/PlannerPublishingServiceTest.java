package org.danteplanner.backend.service;

import org.mockito.InOrder;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.moderation.api.ModeratedPlanner;
import org.danteplanner.backend.planner.service.ModeratorAction;
import org.danteplanner.backend.planner.repository.PlannerModerationRepository;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.moderation.service.RestrictionCheckService;
import org.danteplanner.backend.shared.dto.NotificationSetting;
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
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.entity.PlannerCatalog;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerSubscriptionRepository;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.MockCalls;
import org.danteplanner.backend.support.OwnershipRows;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Unit tests for PlannerPublishingService (publish toggle, owner notification settings).
 */
@ExtendWith(MockitoExtension.class)
class PlannerPublishingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private static final int MAX_PLANNERS_PER_USER = 100;

    private static final int RECOMMENDED_THRESHOLD = 10;

    private static final int CURRENT_SCHEMA_VERSION = 2;

    @Mock
    private PlannerRepository plannerRepository;


    @Mock

    private PlannerContentRepository contentRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerSubscriptionRepository subscriptionRepository;

    @Mock
    private PlannerPublicationRepository publicationRepository;

    @Mock
    private PlannerModerationRepository moderationRepository;

    @Mock
    private DomainEventRepository domainEventRepository;

    private final FakeUsers users = new FakeUsers();

    private PlannerPublishingService publishingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();

        publishingService = publishingServiceWith(TestDataFactory.contentValidator());

        testUser = users.add(TestDataFactory.unsavedUser(1L));
        lenient().when(contentRepository.replaceAtVersion(any(), anyLong(), any())).thenReturn(1);
        lenient().when(contentRepository.migrateContent(any(), anyLong(), any(), any())).thenReturn(1);
        lenient().when(contentRepository.markDeleted(any(), any())).thenReturn(1);
        lenient().when(publicationRepository.publishFirst(any(), any())).thenReturn(1);
        lenient().when(publicationRepository.republish(any())).thenReturn(1);
        lenient().when(publicationRepository.unpublish(any())).thenReturn(1);
        lenient().when(moderationRepository.takeDown(any(), any())).thenReturn(1);
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
        RestrictionCheckService restrictionCheck = new RestrictionCheckService(users, Clock.systemUTC());
        PlannerCatalogService catalogService = new PlannerCatalogService(catalogRepository, plannerRepository,
                plannerStatsRepository, entityFilterRepository,
                keywordFilterRepository, null,
                objectMapper, RECOMMENDED_THRESHOLD);
        PlannerCommandService commandService = new PlannerCommandService(
                plannerRepository,
                contentRepository,
                plannerStatsRepository,
                publicationRepository,
                contentValidator,
                new ContentVersionValidator(TestDataFactory.gameDataRegistry()),
                catalogService,
                accessGuard,
                restrictionCheck,
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                objectMapper, Clock.systemUTC(),
                MAX_PLANNERS_PER_USER,
                CURRENT_SCHEMA_VERSION);
        return new PlannerPublishingService(
                plannerRepository,
                plannerStatsRepository,
                publicationRepository,
                moderationRepository,
                commandService,
                contentValidator,
                catalogService,
                new PlannerSubscriptionService(subscriptionRepository, accessGuard, restrictionCheck, Clock.systemUTC()),
                accessGuard,
                new PlannerOwnershipValidator(),
                new PlannerPublishValidator(),
                new PlannerCategoryValidator(),
                new DomainEventRecorder(domainEventRepository, new ObjectMapper(), event -> { }),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private TestDataFactory.PlannerBuilder testPlannerBuilder() {
        return TestDataFactory.planner(testUser);
    }

    private StoredPlanner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    class PublishIntentTests {

        @Test
        void publishIdempotentStateTargeted_WhenSentTwice_StaysPublished() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            publishedEventsPersist();

            PlannerResponse first = publishingService.publish(testUser.getId(), planner.id());
            when(plannerRepository.findAggregate(planner.id()))
                    .thenReturn(Optional.of(testPlannerBuilder().id(planner.id()).published(true).build()));
            PlannerResponse second = publishingService.publish(testUser.getId(), planner.id());

            assertThat(first.published()).isTrue();
            assertThat(second.published()).as("a repeated publish must not flip the planner back").isTrue();
            verify(catalogRepository, times(1)).insert(any(PlannerCatalog.class));
        }

        @Test
        void publishIdempotentStateTargeted_WhenUnpublishRepeated_StaysUnpublished() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.id());

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
            StoredPlanner planner = testPlannerBuilder().published(false).content(stored).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            publishedEventsPersist();

            PlannerResponse response = publishingServiceWith(renaming).publish(testUser.getId(), planner.id());

            ArgumentCaptor<String> migrated = ArgumentCaptor.forClass(String.class);
            InOrder order = inOrder(contentRepository, catalogRepository);
            order.verify(contentRepository).migrateContent(eq(planner.id()), eq(planner.content().syncVersion()),
                    migrated.capture(), eq(NOW));
            order.verify(catalogRepository).insert(any(PlannerCatalog.class));
            JsonNode published = mapper.readTree(migrated.getValue());
            assertThat(published.path("observationGiftIds")).isEqualTo(mapper.createArrayNode().add("19002"));
            assertThat(response.content()).isEqualTo(migrated.getValue());
            assertThat(response.syncVersion()).isEqualTo(planner.content().syncVersion());
            assertThat(response.lastModifiedAt()).isEqualTo(NOW);
            verify(publicationRepository).publishFirst(planner.id(), NOW);
        }
    }

    @Nested
    class PublishEffectRecordingTests {

        @Test
        void applyPublish_WhenFirstPublish_RecordsTheEventInsteadOfAnnouncing() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            publishedEventsPersist();

            publishingService.publish(testUser.getId(), planner.id());

            ArgumentCaptor<DomainEvent> recorded = ArgumentCaptor.forClass(DomainEvent.class);
            verify(domainEventRepository).insert(recorded.capture());
            verifyNoMoreInteractions(domainEventRepository);
            assertThat(recorded.getValue().getEventType()).isEqualTo(DomainEventType.PLANNER_PUBLISHED);
            assertThat(recorded.getValue().getAggregateId()).isEqualTo(planner.id());
            assertThat(recorded.getValue().getPayload()).isEqualTo("{\"authorId\":" + testUser.getId() + "}");
        }

        @Test
        void applyPublish_WhenAlreadyPublished_RecordsNothing() {
            StoredPlanner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            publishingService.publish(testUser.getId(), planner.id());

            verify(domainEventRepository, never()).insert(any());
            verifyNoInteractions(publicationRepository);
        }

        @Test
        void applyPublish_WhenRepublished_ListsThePlannerWithoutAnEvent() {
            Instant firstAt = NOW.minusSeconds(60);
            StoredPlanner planner = testPlannerBuilder().published(false).firstPublishedAt(firstAt).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            PlannerResponse result = publishingService.publish(testUser.getId(), planner.id());

            assertThat(result.published()).isTrue();
            verify(domainEventRepository, never()).insert(any());
            ArgumentCaptor<PlannerCatalog> row = ArgumentCaptor.forClass(PlannerCatalog.class);
            verify(catalogRepository).insert(row.capture());
            assertThat(row.getValue().getFirstPublishedAt()).isEqualTo(firstAt);
        }

        @Test
        void applyPublish_WhenTheRaceIsLost_ListsAndRecordsNothing() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            when(publicationRepository.publishFirst(planner.id(), NOW)).thenReturn(0);

            PlannerResponse result = publishingService.publish(testUser.getId(), planner.id());

            assertThat(result.published()).isFalse();
            verify(domainEventRepository, never()).insert(any());
            verify(catalogRepository, never()).insert(any());
        }

        @Test
        void applyUnpublish_WhenWithdrawn_RecordsNothing() {
            StoredPlanner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            publishingService.unpublish(testUser.getId(), planner.id());

            verify(domainEventRepository, never()).insert(any());
        }
    }

    @Nested
    class WithdrawFromPublicViewTests {

        @Test
        void withdrawFromPublicView_WhenAlreadyUnpublished_HasNoSideEffects() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));

            ModeratedPlanner result = publishingService.withdrawFromPublicView(
                    planner.id(), new ModeratorAction.Unpublish());

            assertThat(result.published()).isFalse();
            verify(catalogRepository, never()).deleteByPlannerId(any());
        }

        @Test
        void withdrawFromPublicView_WhenPublished_PersistsAndDropsCatalogRow() {
            StoredPlanner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));

            ModeratedPlanner result = publishingService.withdrawFromPublicView(
                    planner.id(), new ModeratorAction.Unpublish());

            assertThat(result.id()).isEqualTo(planner.id());
            assertThat(result.published()).isFalse();
            verify(catalogRepository).deleteByPlannerId(planner.id());
        }

        @Test
        void withdrawFromPublicView_WhenTakedownOfUnpublished_StillPersists() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));

            publishingService.withdrawFromPublicView(planner.id(), new ModeratorAction.TakeDown());

            verify(moderationRepository).takeDown(planner.id(), NOW);
            verify(catalogRepository).deleteByPlannerId(planner.id());
        }

        @Test
        void withdrawFromPublicView_WhenTakedownOfATakenDownUnpublished_HasNoSideEffects() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            when(moderationRepository.takeDown(planner.id(), NOW)).thenReturn(0);

            publishingService.withdrawFromPublicView(planner.id(), new ModeratorAction.TakeDown());

            verify(catalogRepository, never()).deleteByPlannerId(any());
        }
    }

    @Nested
    class LockOrderTests {

        private List<String> plannerCalls() {
            return MockCalls.inOrder(plannerRepository, plannerStatsRepository, catalogRepository,
                    entityFilterRepository, keywordFilterRepository, subscriptionRepository,
                    publicationRepository, moderationRepository);
        }

        private void stored(StoredPlanner planner) {
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
        }

        @Test
        void publish_WhenCalled_LocksTheContentRowRightAfterTheOwnershipCheck() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            stored(planner);
            publishedEventsPersist();

            publishingService.publish(testUser.getId(), planner.id());

            assertThat(plannerCalls()).startsWith("findOwnershipById", "lockContent");
        }

        @Test
        void publishWithContent_WhenCalled_LocksTheContentRowRightAfterTheOwnershipCheck() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            publishedEventsPersist();

            publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.id(),
                    UpsertRequests.resending(planner).title("Locked First").build());

            assertThat(plannerCalls()).startsWith("findOwnershipById", "lockContent");
            assertThat(plannerCalls()).filteredOn("lockContent"::equals).hasSize(1);
        }

        @Test
        void unpublish_WhenCalled_LocksTheContentRowRightAfterTheOwnershipCheck() {
            StoredPlanner planner = testPlannerBuilder().published(true).build();
            stored(planner);

            publishingService.unpublish(testUser.getId(), planner.id());

            assertThat(plannerCalls()).startsWith("findOwnershipById", "lockContent");
        }

        @Test
        void withdrawFromPublicView_WhenCalled_LocksTheContentRowFirst() {
            StoredPlanner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));

            publishingService.withdrawFromPublicView(planner.id(), new ModeratorAction.TakeDown());

            assertThat(plannerCalls()).startsWith("lockContent");
        }

        @Test
        void unpublish_WhenAnotherUserAsks_FailsWithoutTakingTheLock() {
            StoredPlanner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            assertRejected(() -> publishingService.unpublish(testUser.getId() + 1, planner.id()))
                    .withCode("PLANNER_FORBIDDEN");
            assertThat(plannerCalls()).containsExactly("findOwnershipById");
        }
    }

    @Nested
    class PublishWithContentTests {

        @Test
        void publishLoadsAggregateOnce_WhenContentCarried_ReusesUpsertedAggregate() {
            StoredPlanner planner = testPlannerBuilder().published(false).build();
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title("Reused Aggregate").build();

            UUID deviceId = UUID.randomUUID();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            publishedEventsPersist();

            PlannerResponse result = publishingService.publish(
                    testUser.getId(), deviceId, planner.id(), request);

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
            StoredPlanner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            publishedEventsPersist();

            // Act
            PlannerResponse result = publishingService.publish(testUser.getId(), planner.id());

            // Assert
            assertThat(result.published()).isTrue();
            assertThat(result.id()).isEqualTo(planner.id());
            assertThat(result.title()).isEqualTo("Test Planner");
        }

        @Test
        void unpublish_WhenPublished_Unpublishes() {
            // Arrange
            StoredPlanner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            // Act
            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.id());

            // Assert
            assertThat(result.published()).isFalse();
        }

        @Test
        void publish_WhenNotOwner_ThrowsException() {
            // Arrange
            User otherUser = TestDataFactory.unsavedUser(999L);
            StoredPlanner planner = testPlannerBuilder().ownerId(otherUser.getId()).build();

            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            // Act & Assert
            assertRejected(() -> publishingService.publish(testUser.getId(), planner.id()))
                    .withCode("PLANNER_FORBIDDEN")
                    .isInstanceOfSatisfying(PlannerForbiddenException.class,
                            exception -> assertThat(exception.getPlannerId()).isEqualTo(planner.id()));
            verifyNoInteractions(publicationRepository);
        }

        @Test
        void publish_WhenPlannerMissingOrDeleted_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();

            // Act & Assert
            assertRejected(() -> publishingService.publish(testUser.getId(), nonExistentId))
                    .withCode("PLANNER_NOT_FOUND");
        }

        @Test
        void publish_WhenPublishing_AutoSubscribesOwner() {
            // Arrange
            StoredPlanner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            publishedEventsPersist();

            // Act
            PlannerResponse result = publishingService.publish(testUser.getId(), planner.id());

            // Assert
            assertThat(result.published()).isTrue();
            // The subscription is a row in another aggregate and never reaches this response;
            // asserting it as state needs a containerized test with a real subscription repository.
            verify(subscriptionRepository).insertIfAbsent(eq(testUser.getId()), eq(planner.id()), any());
        }

        @Test
        void unpublish_WhenUnpublishing_DoesNotAutoSubscribe() {
            // Arrange
            StoredPlanner planner = testPlannerBuilder().published(true).build();

            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            // Act
            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.id());

            // Assert
            assertThat(result.published()).isFalse();
            verifyNoInteractions(subscriptionRepository);
        }
    }

    @Nested
    class BanEnforcementTests {

        @Test
        void unpublish_WhenBannedOwner_Unpublishes() {
            TestDataFactory.ban(testUser, java.time.Instant.now(), 1L);
            StoredPlanner planner = testPlannerBuilder().published(true).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            PlannerResponse result = publishingService.unpublish(testUser.getId(), planner.id());

            assertThat(result.published()).isFalse();
            verify(catalogRepository).deleteByPlannerId(planner.id());
        }
    }

    @Nested
    class ToggleOwnerNotificationsTests {

        @Test
        void toggleOwnerNotifications_WhenOwner_UpdatesSetting() {
            // Arrange
            StoredPlanner planner = createTestPlanner();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            when(publicationRepository.setOwnerNotifications(planner.id(), false)).thenReturn(1);

            // Act
            ToggleOwnerNotificationsResponse response =
                    publishingService.toggleOwnerNotifications(testUser.getId(), planner.id(), NotificationSetting.OFF);

            // Assert
            assertThat(response.ownerNotificationsEnabled()).isFalse();
            verify(publicationRepository).setOwnerNotifications(planner.id(), false);
            verifyNoMoreInteractions(publicationRepository);
        }

        @Test
        void toggleOwnerNotifications_WhenNotOwner_ThrowsException() {
            // Arrange
            User otherUser = TestDataFactory.unsavedUser(999L);
            StoredPlanner planner = testPlannerBuilder().ownerId(otherUser.getId()).build();

            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            // Act & Assert
            assertRejected(() -> publishingService.toggleOwnerNotifications(testUser.getId(), planner.id(), NotificationSetting.ON))
                    .withCode("PLANNER_FORBIDDEN");
            verifyNoInteractions(publicationRepository);
        }
    }

    @Nested
    class PublishValidatesBeforeMutatingTests {

        @Test
        void publishValidatesBeforeMutating_WhenTitleBlank_LeavesAggregateUnpublished() {
            StoredPlanner planner = testPlannerBuilder().title("   ").published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            assertRejected(() -> publishingService.publish(testUser.getId(), planner.id()))
                    .withViolation("MISSING_TITLE")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            thrown -> assertThat(thrown.getOriginalCode()).isEqualTo("MISSING_TITLE"));
            verifyNoInteractions(publicationRepository);
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleBlankAndVersionUnknown_RefusesTheSaveBeforeTheTitle() {
            StoredPlanner planner = testPlannerBuilder().title("Stored Title").published(false).build();
            lenient().when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .id(planner.id().toString())
                    .title("  ")
                    .status(null)
                    .content("{}")
                    .contentVersion(99)
                    .build();

            assertRejected(() -> publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.id(),
                    request))
                    .withCode("SYNC_CONFLICT");
            verify(contentRepository, never()).replaceAtVersion(any(), anyLong(), any());
            verifyNoInteractions(publicationRepository);
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleBlank_RefusesTheTitleAfterTheSaveWithoutPublishing() {
            StoredPlanner planner = testPlannerBuilder().title("Stored Title").published(false).build();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title("  ").build();

            assertRejected(() -> publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.id(),
                    request))
                    .withViolation("MISSING_TITLE")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            thrown -> assertThat(thrown.getOriginalCode()).isEqualTo("MISSING_TITLE"));
            verifyNoInteractions(publicationRepository, catalogRepository);
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleAbsentAndStoredBlank_RefusesTitleWithoutPublishing() {
            StoredPlanner planner = testPlannerBuilder().title("   ").published(false).build();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title(null).build();

            assertRejected(() -> publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.id(),
                    request))
                    .withViolation("MISSING_TITLE")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            thrown -> assertThat(thrown.getOriginalCode()).isEqualTo("MISSING_TITLE"));
            verifyNoInteractions(publicationRepository, catalogRepository);
        }

        @Test
        void publishValidatesBeforeMutating_WhenBodyTitleAbsentAndStoredPresent_Publishes() {
            StoredPlanner planner = testPlannerBuilder().title("Stored Title").published(false).build();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner).title(null).build();
            UUID deviceId = UUID.randomUUID();
            publishedEventsPersist();

            PlannerResponse result = publishingService.publish(
                    testUser.getId(), deviceId, planner.id(), request);

            assertThat(result.published()).isTrue();
            assertThat(result.title()).isEqualTo("Stored Title");
        }

        @Test
        void publish_WhenTheStoredCategoryIsInvalid_LogsTheRefusalOnce() {
            StoredPlanner planner = refractedRailwayPlanner();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            try (LogCapture capture = LogCapture.onThisThread(
                    PlannerPublishingService.class, PlannerCommandService.class)) {
                assertRejected(() -> publishingService.publish(testUser.getId(), planner.id()))
                        .withViolation("INVALID_CATEGORY", "");
                assertThat(capture.at(Level.WARN))
                        .extracting(ILoggingEvent::getLoggerName, ILoggingEvent::getFormattedMessage)
                        .containsExactly(tuple(PlannerPublishingService.class.getName(),
                                "Validation failed: Invalid category: RR_PLACEHOLDER"));
            }
        }

        @Test
        void publish_WhenTheCarriedCategoryIsInvalid_LogsTheRefusalOnce() {
            StoredPlanner planner = refractedRailwayPlanner();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner).build();

            try (LogCapture capture = LogCapture.onThisThread(
                    PlannerPublishingService.class, PlannerCommandService.class)) {
                assertRejected(() -> publishingService.publish(testUser.getId(), UUID.randomUUID(), planner.id(),
                        request))
                        .withViolation("INVALID_CATEGORY", "");
                assertThat(capture.at(Level.WARN)).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("Validation failed: Invalid category: RR_PLACEHOLDER");
            }
        }

        private StoredPlanner refractedRailwayPlanner() {
            return testPlannerBuilder()
                    .plannerType(PlannerType.REFRACTED_RAILWAY)
                    .category("RR_PLACEHOLDER")
                    .published(false)
                    .build();
        }

        @Test
        void publishValidatesBeforeMutating_WhenContentRefused_LeavesAggregateUnpublished() {
            StoredPlanner planner = testPlannerBuilder().content("").published(false).build();
            when(plannerRepository.findAggregate(planner.id())).thenReturn(Optional.of(planner));
            lenient().when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            assertRejected(() -> publishingService.publish(testUser.getId(), planner.id()))
                    .withCode("EMPTY_CONTENT");

            verifyNoInteractions(publicationRepository);
            verify(catalogRepository, never()).insert(any());
        }
    }
}
