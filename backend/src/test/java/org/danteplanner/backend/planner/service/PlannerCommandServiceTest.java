package org.danteplanner.backend.planner.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.Level;
import org.danteplanner.backend.planner.repository.PlannerPublicationRepository;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.PlannerResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.repository.ContentWrite;
import org.danteplanner.backend.planner.repository.PlannerContentRepository;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.support.MockCalls;
import org.danteplanner.backend.support.OwnershipRows;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.RecordingRestrictionCheck;
import org.danteplanner.backend.support.RecordingRestrictionCheck.Decision;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.planner.exception.PlannerConcurrentWriteException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.danteplanner.backend.support.UpsertRequests;
import org.danteplanner.backend.planner.sync.SyncVersionValidator.WriteMode;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Unit tests for PlannerCommandService (owner CRUD write operations:
 * create/upsert/update/delete).
 */
@ExtendWith(MockitoExtension.class)
class PlannerCommandServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerContentRepository contentRepository;

    @Mock
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

    @Mock
    private PlannerPublicationRepository publicationRepository;

    private PlannerCommandService commandService;

    private RecordingRestrictionCheck restrictionCheck;

    private static final int MAX_PLANNERS_PER_USER = 100;

    private static final int RECOMMENDED_THRESHOLD = 10;

    private static final int CURRENT_SCHEMA_VERSION = 2;

    private static final String UNKNOWN_IDENTITY_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"identity\":{\"id\":\"10101\"", "\"identity\":{\"id\":\"10199\"");

    private final FakeUsers users = new FakeUsers();

    private User testUser;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        restrictionCheck = new RecordingRestrictionCheck(users);
        commandService = commandServiceWith(TestDataFactory.contentValidator());

        testUser = users.add(TestDataFactory.unsavedUser(1L));

        deviceId = UUID.randomUUID();
        lenient().when(contentRepository.replaceAtVersion(any(), anyLong(), any())).thenReturn(1);
        lenient().when(contentRepository.markDeleted(any(), any())).thenReturn(1);
    }

    private PlannerCommandService commandServiceWith(PlannerContentValidator contentValidator) {
        return new PlannerCommandService(
                plannerRepository,
                contentRepository,
                statsRepository,
                publicationRepository,
                contentValidator,
                new ContentVersionValidator(TestDataFactory.gameDataRegistry()),
                new PlannerCatalogService(catalogRepository, plannerRepository, statsRepository,
                        entityFilterRepository, keywordFilterRepository,
                        null, new ObjectMapper(),
                        RECOMMENDED_THRESHOLD),
                new PlannerAccessGuard(users, plannerRepository),
                restrictionCheck,
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                new ObjectMapper(), Clock.systemUTC(),
                MAX_PLANNERS_PER_USER,
                CURRENT_SCHEMA_VERSION
        );
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private PlannerContent insertedContent() {
        ArgumentCaptor<PlannerContent> content = ArgumentCaptor.forClass(PlannerContent.class);
        verify(plannerRepository).insertAggregate(any(), content.capture(), any(), any());
        return content.getValue();
    }

    private ContentWrite replacedWith(StoredPlanner planner) {
        ArgumentCaptor<ContentWrite> write = ArgumentCaptor.forClass(ContentWrite.class);
        verify(contentRepository).replaceAtVersion(eq(planner.id()), eq(planner.content().syncVersion()),
                write.capture());
        return write.getValue();
    }

    private TestDataFactory.PlannerBuilder resendable(long syncVersion, boolean published, String content) {
        return TestDataFactory.planner(testUser)
                .title("Test Planner")
                .status(PlannerStatus.DRAFT)
                .content(content)
                .published(published)
                .syncVersion(syncVersion)
                .schemaVersion(CURRENT_SCHEMA_VERSION)
                .deviceId(deviceId);
    }

    private StoredPlanner testPlanner(long syncVersion, boolean published) {
        return testPlanner(syncVersion, published, "{\"data\": \"test\"}");
    }

    private StoredPlanner testPlanner(long syncVersion, boolean published, String content) {
        return TestDataFactory.planner(testUser)
                .title("Test Planner")
                .status(PlannerStatus.DRAFT)
                .content(content)
                .published(published)
                .syncVersion(syncVersion)
                .build();
    }

    @Nested
    class CreatePlannerTests {

        @Test
        void createPlanner_WhenWithinLimit_Succeeds() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.draft().title("Test Planner").category("5F").build();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(50L);

            // Act
            PlannerResponse response = commandService.createPlanner(testUser.getId(), deviceId, request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.title()).isEqualTo("Test Planner");
            assertThat(response.category()).isEqualTo("5F");
            assertThat(response.syncVersion()).isEqualTo(1L);
        }

        @Test
        void createPlanner_WhenAtLimit_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.valid();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) MAX_PLANNERS_PER_USER);

            // Act & Assert
            assertRejected(() -> commandService.createPlanner(testUser.getId(), deviceId, request))
                    .withCode("PLANNER_LIMIT_EXCEEDED")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .contains(String.valueOf(MAX_PLANNERS_PER_USER)));
            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
        }

        @Test
        void createPlanner_WhenUserNotFound_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.valid();
            Long nonExistentUserId = 999L;

            // Act & Assert
            assertRejected(() -> commandService.createPlanner(nonExistentUserId, deviceId, request))
                    .withCode("USER_NOT_FOUND")
                    .isInstanceOfSatisfying(UserNotFoundException.class, exception -> {
                        assertThat(exception.getMessage())
                                    .isEqualTo("User not found with id: " + nonExistentUserId);
                        assertThat(exception.getMessage()).contains(nonExistentUserId.toString());
                    });
            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
        }

        @Test
        void createPlanner_WhenNoTitle_UsesDefault() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.draft().title(null).build();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);

            // Act
            commandService.createPlanner(testUser.getId(), deviceId, request);

            // Assert
            assertThat(insertedContent().getTitle()).isEqualTo("Untitled");
        }

        @Test
        void createPlanner_WhenCalled_ValidatesContentBeforeSave() {
            // Arrange
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            UpsertPlannerRequest unknownIdentity = UpsertRequests.draft().content(UNKNOWN_IDENTITY_CONTENT).build();

            // Act & Assert
            assertRejected(() -> commandService.createPlanner(testUser.getId(), deviceId, unknownIdentity))
                    .withViolation("UNKNOWN_IDENTITY");
            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
        }

        @Test
        void createPlanner_WhenTheCategoryIsInvalid_LogsTheRefusal() {
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .plannerType(PlannerType.REFRACTED_RAILWAY)
                    .category("RR_PLACEHOLDER")
                    .contentVersion(5)
                    .build();

            try (LogCapture capture = LogCapture.onThisThread(PlannerCommandService.class)) {
                assertRejected(() -> commandService.createPlanner(testUser.getId(), deviceId, request))
                        .withViolation("INVALID_CATEGORY", "");
                assertThat(capture.at(Level.WARN)).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("Validation failed: Invalid category: RR_PLACEHOLDER");
            }
        }

        @Test
        void createPlanner_WhenInvalidContentVersion_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .contentVersion(5) // Old version
                    .content("{\"equipment\":")
                    .build();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);

            // Act & Assert
            assertRejected(() -> commandService.createPlanner(testUser.getId(), deviceId, request))
                    .withViolation("INVALID_CONTENT_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            exception -> assertThat(exception.getOriginalCode()).isEqualTo("INVALID_CONTENT_VERSION"));
            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
        }
    }

    @Nested
    class UpsertCategoryTests {

        @TempDir
        private Path dataDir;

        private PlannerContentValidator validatorRenaming(String idMigrations) throws IOException {
            try (var entries = Files.list(Path.of("../static/data"))) {
                for (Path entry : entries.toList()) {
                    Files.createSymbolicLink(dataDir.resolve(entry.getFileName()), entry.toAbsolutePath());
                }
            }
            Files.writeString(dataDir.resolve(GameDataRegistry.ID_MIGRATIONS_FILE), idMigrations);
            ObjectMapper mapper = new ObjectMapper();
            GameDataRegistry registry = new GameDataRegistry(new GameDataLoader(mapper), dataDir.toString());
            registry.init();
            return new PlannerContentValidator(mapper, 51200, 1024, registry, new SinnerIdValidator(),
                    new FloorRules(registry));
        }

        @Test
        void upsertPlanner_WhenCategoryAndContentVersionChange_ValidatesTheContent() {
            StoredPlanner planner = testPlanner(1L, false, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner)
                    .category("10F")
                    .contentVersion(99)
                    .build();

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(), request,
                    WriteMode.CHECKED))
                    .withViolation("UNKNOWN_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            exception -> assertThat(exception.getSubErrors()).isEqualTo(java.util.List.of(
                                    new ValidationError("UNKNOWN_VERSION", "",
                                            "No game data for content version 99"))));
        }

        @Test
        void upsertPlanner_WhenTheCategoryIsInvalid_LogsTheRefusal() {
            StoredPlanner planner = TestDataFactory.planner(testUser)
                    .plannerType(PlannerType.REFRACTED_RAILWAY)
                    .category("RR_PLACEHOLDER")
                    .content(TestDataFactory.VALID_CONTENT)
                    .build();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner).build();

            try (LogCapture capture = LogCapture.onThisThread(PlannerCommandService.class)) {
                assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(),
                        request, WriteMode.CHECKED))
                        .withViolation("INVALID_CATEGORY", "");
                assertThat(capture.at(Level.WARN)).extracting(ILoggingEvent::getFormattedMessage)
                        .containsExactly("Validation failed: Invalid category: RR_PLACEHOLDER");
            }
        }

        @Test
        void upsertPlanner_WhenAStaleResendOmitsTitleAndStatus_AcksKeepingTheStoredOnes() {
            StoredPlanner planner = resendable(3L, false, TestDataFactory.VALID_CONTENT)
                    .status(PlannerStatus.SAVED).build();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner)
                    .title(null)
                    .status(null)
                    .syncVersion(2L)
                    .build();

            SaveOutcome result = commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(),
                    request, WriteMode.CHECKED);

            assertThat(result).isInstanceOf(SaveOutcome.Acknowledged.class);
            assertThat(result.planner().content().syncVersion()).isEqualTo(3L);
            assertThat(result.planner().content().title()).isEqualTo("Test Planner");
            assertThat(result.planner().content().status()).isEqualTo(PlannerStatus.SAVED);
            verify(contentRepository, never()).replaceAtVersion(any(), anyLong(), any());
        }

        @Test
        void upsertPlanner_WhenCategoryIsUnchanged_ValidatesTheContent() {
            StoredPlanner planner = testPlanner(1L, false, UNKNOWN_IDENTITY_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest retitled = UpsertRequests.resending(planner).title("Renamed").build();

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(), retitled,
                    WriteMode.CHECKED))
                    .withViolation("UNKNOWN_IDENTITY");
        }

        @Test
        void upsertPlanner_WhenAPublishedPlannersCategoryChanges_AdmitsAtPublishAndStoresTheNormalizedContent()
                throws Exception {
            PlannerContentValidator renaming = validatorRenaming("{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}");
            String stored = TestDataFactory.VALID_CONTENT.replace(
                    "\"observationGiftIds\":[]", "\"observationGiftIds\":[\"19247\"]");
            StoredPlanner planner = TestDataFactory.planner(testUser).title("Test Planner").status(PlannerStatus.DRAFT)
                    .content(stored).published(true).category("10F").build();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            commandServiceWith(renaming).upsertPlanner(testUser.getId(), deviceId, planner.id(),
                    UpsertRequests.resending(planner).category("5F").build(), WriteMode.CHECKED);

            ContentWrite write = replacedWith(planner);
            JsonNode saved = new ObjectMapper().readTree(write.json());
            assertThat(saved.path("observationGiftIds")).isEqualTo(new ObjectMapper().createArrayNode().add("19002"));
            assertThat(write.category()).isEqualTo("5F");
        }
    }

    @Nested
    class LockOrderTests {

        private List<String> plannerCalls() {
            return MockCalls.inOrder(plannerRepository, statsRepository, catalogRepository,
                    entityFilterRepository, keywordFilterRepository);
        }

        @Test
        void upsertPlanner_WhenThePlannerExists_LocksTheContentRowBeforeLoadingIt() {
            StoredPlanner planner = testPlanner(1L, false, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(),
                    UpsertRequests.resending(planner).title("Locked First").build(), WriteMode.CHECKED);

            assertThat(plannerCalls()).startsWith("findOwnershipById", "lockContent", "findAggregateForOwner");
        }

        @Test
        void deletePlanner_WhenCalled_LocksTheContentRowBeforeLoadingIt() {
            StoredPlanner planner = testPlanner(1L, true);
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            TransactionSynchronizationManager.initSynchronization();

            commandService.deletePlanner(testUser.getId(), planner.id());

            assertThat(plannerCalls()).startsWith("findOwnershipById", "lockContent", "findAggregateForOwner");
        }

        @Test
        void deletePlanner_WhenAnotherUserAsks_FailsWithoutTakingTheLock() {
            StoredPlanner planner = testPlanner(1L, true);
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            assertRejected(() -> commandService.deletePlanner(testUser.getId() + 1, planner.id()))
                    .withCode("PLANNER_NOT_FOUND");
            assertThat(plannerCalls()).containsExactly("findOwnershipById");
        }
    }

    @Nested
    class DeletePlannerTests {

        @Test
        void deletePlanner_WhenNotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();

            // Act & Assert
            assertRejected(() -> commandService.deletePlanner(testUser.getId(), plannerId))
                    .withCode("PLANNER_NOT_FOUND");

            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
        }

        @Test
        void deletePlanner_WhenPublishedPlanner_UnpublishesFirst() {
            // Arrange
            StoredPlanner planner = testPlanner(1L, true);
            assertThat(planner.publication().isPublished()).isTrue();

            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            TransactionSynchronizationManager.initSynchronization();

            // Act
            commandService.deletePlanner(testUser.getId(), planner.id());

            // Assert
            verify(publicationRepository).unpublish(planner.id());
            verify(contentRepository).markDeleted(eq(planner.id()), any());
        }

        @Test
        void deletePlanner_WhenUnpublishedPlanner_NoPublishChange() {
            // Arrange
            StoredPlanner planner = testPlanner(1L, false);

            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            TransactionSynchronizationManager.initSynchronization();

            // Act
            commandService.deletePlanner(testUser.getId(), planner.id());

            // Assert
            verifyNoInteractions(publicationRepository);
            verify(contentRepository).markDeleted(eq(planner.id()), any());
        }
    }

    @Nested
    class PlannerLimitEdgeCaseTests {

        @Test
        void createPlanner_WhenAtMaxMinusOne_Succeeds() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.draft().title("Test Planner").build();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) (MAX_PLANNERS_PER_USER - 1));

            // Act & Assert - should not throw
            PlannerResponse response = commandService.createPlanner(testUser.getId(), deviceId, request);

            assertThat(response.id()).isEqualTo(UUID.fromString(request.id()));
            assertThat(response.title()).isEqualTo("Test Planner");
            assertThat(response.syncVersion()).isEqualTo(1L);
        }

        @Test
        void createPlanner_WhenAtMax_ThrowsLimitExceeded() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.valid();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) MAX_PLANNERS_PER_USER);

            // Act & Assert
            assertRejected(() -> commandService.createPlanner(testUser.getId(), deviceId, request))
                    .withCode("PLANNER_LIMIT_EXCEEDED");

            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
        }

        @Test
        void createPlanner_WhenCheckingLimit_CountsOnlyNonDeleted() {
            UpsertPlannerRequest request = UpsertRequests.valid();
            // Only the deleted-excluding count is stubbed, and with a value no other source could
            // supply, so the verdict can only carry it if that count is what the limit reads.
            long activeCount = MAX_PLANNERS_PER_USER + 7L;
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(activeCount);

            assertRejected(() -> commandService.createPlanner(testUser.getId(), deviceId, request))
                    .withCode("PLANNER_LIMIT_EXCEEDED")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).contains(String.valueOf(activeCount)));
            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
        }
    }

    @Nested
    class BanEnforcementTests {

        @Test
        void upsertPlanner_WhenBannedUser_IsNotBlockedByTheGuard() {
            TestDataFactory.ban(testUser, java.time.Instant.now(), 1L);
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);

            UpsertPlannerRequest request = UpsertRequests.draft().id(null).status(null).build();

            UUID plannerId = UUID.randomUUID();

            // A ban withdraws distribution (publish, comment), never possession.
            SaveOutcome result = commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, WriteMode.CHECKED);

            assertThat(result).as("a banned user must keep private planner work").isInstanceOf(SaveOutcome.Created.class);
            assertThat(result.planner().id()).isEqualTo(plannerId);
        }

        @Test
        void upsertPlanner_WhenNonBannedUser_Succeeds() {
            // Arrange
            when(plannerRepository.countActiveByUserId(testUser.getId()))
                    .thenReturn(0L);

            UpsertPlannerRequest request = UpsertRequests.draft()
                    .id(null)
                    .title("Test Planner")
                    .status(null)
                    .build();

            UUID plannerId = UUID.randomUUID();

            // Act
            SaveOutcome result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, WriteMode.CHECKED);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result).isInstanceOf(SaveOutcome.Created.class);
            assertThat(result.planner().id()).isEqualTo(plannerId);
            assertThat(result.planner().content().title()).isEqualTo("Test Planner");
        }
    }

    @Nested
    class PublishedEditRestrictionTests {

        @Test
        void upsertPlanner_WhenBannedOwnerEditsAPublishedPlanner_RefusesWithUserBanned() {
            TestDataFactory.ban(testUser, Instant.now(), 2L);
            StoredPlanner planner = testPlanner(1L, true, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest retitled = UpsertRequests.resending(planner).title("Renamed").build();

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(), retitled,
                    WriteMode.CHECKED)).withCode("USER_BANNED");
            verify(contentRepository, never()).replaceAtVersion(any(), anyLong(), any());
        }

        @Test
        void upsertPlanner_WhenTimedOutOwnerEditsAPublishedPlanner_RefusesBeforeTheSyncVersionCheck() {
            TestDataFactory.timeOut(testUser, Instant.now().plusSeconds(3600));
            StoredPlanner planner = testPlanner(5L, true, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest stale = UpsertRequests.resending(planner).title("Renamed").syncVersion(1L).build();

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(), stale,
                    WriteMode.CHECKED)).withCode("USER_TIMED_OUT");
            assertThat(restrictionCheck.decisions()).containsExactly(Decision.CHECKED);
        }

        @Test
        void upsertPlanner_WhenBannedOwnerEditsAPrivatePlanner_SavesAndRecordsAPrivateDecision() {
            TestDataFactory.ban(testUser, Instant.now(), 2L);
            StoredPlanner planner = testPlanner(1L, false, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest retitled = UpsertRequests.resending(planner).title("Renamed").build();

            SaveOutcome result = commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(), retitled,
                    WriteMode.CHECKED);

            assertThat(result.planner().content().title()).isEqualTo("Renamed");
            assertThat(replacedWith(planner).title()).isEqualTo("Renamed");
            assertThat(restrictionCheck.decisions()).containsExactly(Decision.PRIVATE);
        }

        @Test
        void upsertPlanner_WhenCreatingAPlanner_RecordsAPrivateDecision() {
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);

            commandService.upsertPlanner(testUser.getId(), deviceId, UUID.randomUUID(),
                    UpsertRequests.draft().id(null).status(null).build(), WriteMode.CHECKED);

            assertThat(restrictionCheck.decisions()).containsExactly(Decision.PRIVATE);
        }
    }

    @Nested
    class UpsertSoftDeleteGuardTests {

        private static PlannerOwnershipRow ownership(Long userId, Instant deletedAt) {
            return new PlannerOwnershipRow() {
                @Override
                public Long getUserId() {
                    return userId;
                }

                @Override
                public Instant getDeletedAt() {
                    return deletedAt;
                }
            };
        }

        @Test
        void createOwnershipCheck_WhenOwnSoftDeleted_ThrowsNotFoundWithoutAnExistenceProbe() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = UpsertRequests.draft().id(null).status(null).build();
            PlannerOwnershipRow ownership = ownership(testUser.getId(), Instant.now());
            when(plannerRepository.findOwnershipById(plannerId))
                    .thenReturn(Optional.of(ownership));

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, WriteMode.CHECKED))
                    .withCode("PLANNER_NOT_FOUND");
            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
            verify(plannerRepository, never()).countActiveByUserId(any());
        }

        @Test
        void createOwnershipCheck_WhenOtherUserActive_ThrowsForbiddenWithoutAnExistenceProbe() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = UpsertRequests.draft().id(null).status(null).build();
            PlannerOwnershipRow ownership = ownership(testUser.getId() + 1, null);
            when(plannerRepository.findOwnershipById(plannerId))
                    .thenReturn(Optional.of(ownership));

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, WriteMode.CHECKED))
                    .withCode("PLANNER_FORBIDDEN");
            verify(plannerRepository, never()).insertAggregate(any(), any(), any(), any());
        }

        @Test
        void createOwnershipCheck_WhenGenuinelyNew_CreatesWithoutAnExistenceProbe() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = UpsertRequests.draft().id(null).status(null).build();
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            when(plannerRepository.findOwnershipById(plannerId))
                    .thenReturn(Optional.empty());
            when(plannerRepository.countActiveByUserId(testUser.getId()))
                    .thenReturn(0L);

            SaveOutcome result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, WriteMode.CHECKED);

            assertThat(result).isInstanceOf(SaveOutcome.Created.class);
        }
    }

    @Nested
    class WriteModeTests {

        private List<String> infoOf(WriteMode mode, StoredPlanner planner, UpsertPlannerRequest request) {
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            return LogCapture.during(() -> commandService.upsertPlanner(
                            testUser.getId(), deviceId, planner.id(), request, mode),
                    Level.INFO, PlannerCommandService.class).stream().map(ILoggingEvent::getFormattedMessage).toList();
        }

        @Test
        void upsertPlanner_WhenForcedAtAStaleVersion_WritesAndLogsTheForce() {
            StoredPlanner planner = testPlanner(3L, false, TestDataFactory.VALID_CONTENT);
            UpsertPlannerRequest stale = UpsertRequests.resending(planner).syncVersion(1L).build();

            assertThat(infoOf(WriteMode.FORCED, planner, stale)).containsExactly(
                    "Planner " + planner.id() + " exists for user 1, updating (force=true)",
                    "Updated planner " + planner.id() + " via upsert, new syncVersion: 4");
            assertThat(replacedWith(planner).syncVersion()).isEqualTo(4L);
        }

        @Test
        void upsertPlanner_WhenAStaleResendMovesNothing_LogsTheAcknowledgement() {
            StoredPlanner planner = resendable(3L, false, TestDataFactory.VALID_CONTENT).build();
            UpsertPlannerRequest stale = UpsertRequests.resending(planner).syncVersion(1L).build();

            assertThat(infoOf(WriteMode.CHECKED, planner, stale)).containsExactly(
                    "Planner " + planner.id() + " exists for user 1, updating (force=false)",
                    "Upsert of planner " + planner.id()
                            + " would move no field, acknowledging syncVersion 3 without a write");
        }

        @Test
        void upsertPlanner_WhenThePlannerIsDeletedBeforeTheLock_RefusesOnTheRowReadAfterIt() {
            StoredPlanner planner = testPlanner(1L, false, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(testUser.getId(), null)))
                    .thenReturn(Optional.of(OwnershipRows.of(testUser.getId(), Instant.EPOCH)));
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.empty());

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(),
                    UpsertRequests.resending(planner).build(), WriteMode.FORCED))
                    .withCode("PLANNER_NOT_FOUND");
            assertThat(MockCalls.inOrder(plannerRepository)).containsExactly(
                    "findOwnershipById", "lockContent", "findAggregateForOwner", "findOwnershipById");
            assertThat(restrictionCheck.decisions()).isEmpty();
        }
    }

    @Nested
    class KeywordDerivationTests {

        private static final String SINKING_CONTENT = TestDataFactory.withSelectedKeywords(
                TestDataFactory.VALID_CONTENT, java.util.Set.of("Sinking"));
        private static final String BURST_CONTENT = TestDataFactory.withSelectedKeywords(
                TestDataFactory.VALID_CONTENT, java.util.Set.of("Burst"));

        private StoredPlanner storedWithSinking() {
            StoredPlanner planner = resendable(3L, false, SINKING_CONTENT).build();
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            return planner;
        }

        @Test
        void createPlanner_WhenTopLevelKeywordsDisagreeWithContent_StoresTheContentKeywords() {
            UpsertPlannerRequest request = UpsertRequests.draft()
                    .status(null)
                    .content(SINKING_CONTENT)
                    .selectedKeywords(java.util.Set.of("Burst"))
                    .build();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            commandService.createPlanner(testUser.getId(), deviceId, request);

            assertThat(insertedContent().getSelectedKeywords()).isEqualTo(java.util.Set.of("Sinking"));
        }

        @Test
        void upsertPlanner_WhenTopLevelKeywordsAreAbsent_DerivesTheColumnFromTheNewContent() {
            StoredPlanner planner = storedWithSinking();

            commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(),
                    UpsertRequests.resending(planner).content(BURST_CONTENT).build(), WriteMode.CHECKED);

            assertThat(replacedWith(planner).keywords()).isEqualTo(java.util.Set.of("Burst"));
        }

        @Test
        void upsertPlanner_WhenAStaleSaveDiffersOnlyInTopLevelKeywords_AcknowledgesWithoutConflict() {
            StoredPlanner planner = storedWithSinking();
            long storedVersion = planner.content().syncVersion();

            SaveOutcome result = commandService.upsertPlanner(testUser.getId(), deviceId,
                    planner.id(), UpsertRequests.resending(planner)
                            .content(SINKING_CONTENT)
                            .selectedKeywords(java.util.Set.of("Burst"))
                            .syncVersion(storedVersion - 1)
                            .build(),
                    WriteMode.CHECKED);

            assertThat(result.planner().content().syncVersion()).isEqualTo(storedVersion);
            assertThat(result.planner().content().keywords()).isEqualTo(java.util.Set.of("Sinking"));
            verify(contentRepository, never()).replaceAtVersion(any(), anyLong(), any());
        }
    }

    @Nested
    class ConcurrentWriteTests {

        @Test
        void upsertPlanner_WhenTheVersionedUpdateMatchesNoRow_RefusesWithoutTouchingTheCatalogOrDeclaringFreshness() {
            StoredPlanner planner = testPlanner(1L, true, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(contentRepository.replaceAtVersion(eq(planner.id()), eq(1L), any())).thenReturn(0);
            FreshnessDeclarations.begin();
            try {
                assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.id(),
                        UpsertRequests.resending(planner).title("Renamed").build(), WriteMode.CHECKED))
                        .withCode("CONCURRENT_WRITE")
                        .isInstanceOf(PlannerConcurrentWriteException.class);

                assertThat(FreshnessDeclarations.declared()).isEmpty();
            } finally {
                FreshnessDeclarations.clear();
            }
            verifyNoInteractions(catalogRepository, entityFilterRepository, keywordFilterRepository);
        }

        @Test
        void deletePlanner_WhenTheTombstoneUpdateMatchesNoRow_RefusesAsAbsent() {
            StoredPlanner planner = testPlanner(1L, false);
            when(plannerRepository.findOwnershipById(planner.id()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            when(plannerRepository.findAggregateForOwner(planner.id(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(contentRepository.markDeleted(eq(planner.id()), any())).thenReturn(0);

            assertRejected(() -> commandService.deletePlanner(testUser.getId(), planner.id()))
                    .withCode("PLANNER_NOT_FOUND");
            verifyNoInteractions(catalogRepository);
        }
    }
}
