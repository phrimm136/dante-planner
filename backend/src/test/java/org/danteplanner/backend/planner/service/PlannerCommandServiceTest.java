package org.danteplanner.backend.planner.service;

import org.danteplanner.backend.planner.dto.UpsertResult;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.PlannerResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.entity.PlannerContentLifecycle;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.StoredPlannerContent;
import org.danteplanner.backend.support.MockCalls;
import org.danteplanner.backend.support.OwnershipRows;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.RecordingRestrictionCheck;
import org.danteplanner.backend.support.RecordingRestrictionCheck.Decision;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerCatalogRepository;
import org.danteplanner.backend.planner.repository.PlannerEntityFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerKeywordFilterRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private PlannerEntityFilterRepository entityFilterRepository;

    @Mock
    private PlannerKeywordFilterRepository keywordFilterRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private PlannerCatalogRepository catalogRepository;

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
    }

    private PlannerCommandService commandServiceWith(PlannerContentValidator contentValidator) {
        return new PlannerCommandService(
                plannerRepository,
                statsRepository,
                contentValidator,
                new ContentVersionValidator(TestDataFactory.gameDataRegistry()),
                new PlannerCatalogService(catalogRepository, plannerRepository, statsRepository,
                        entityFilterRepository, keywordFilterRepository,
                        null, new ObjectMapper(),
                        new RecordingTombstoneStore(), RECOMMENDED_THRESHOLD),
                new PlannerAccessGuard(users, plannerRepository),
                restrictionCheck,
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                new ObjectMapper(),
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

    private Planner testPlanner(long syncVersion, boolean published) {
        return testPlanner(syncVersion, published, "{\"data\": \"test\"}");
    }

    private Planner testPlanner(long syncVersion, boolean published, String content) {
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
            when(plannerRepository.insert(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return PlannerContentLifecycle.asPersisted(planner);
            });

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
            verify(plannerRepository, never()).insert(any());
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
                        assertThat(exception.getUserId()).isEqualTo(nonExistentUserId);
                        assertThat(exception.getMessage()).contains(nonExistentUserId.toString());
                    });
            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void createPlanner_WhenNoTitle_UsesDefault() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.draft().title(null).build();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);

            ArgumentCaptor<Planner> plannerCaptor = ArgumentCaptor.forClass(Planner.class);
            when(plannerRepository.insert(plannerCaptor.capture())).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return PlannerContentLifecycle.asPersisted(planner);
            });

            // Act
            commandService.createPlanner(testUser.getId(), deviceId, request);

            // Assert
            assertThat(plannerCaptor.getValue().getTitle()).isEqualTo("Untitled");
        }

        @Test
        void createPlanner_WhenCalled_ValidatesContentBeforeSave() {
            // Arrange
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            UpsertPlannerRequest unknownIdentity = UpsertRequests.draft().content(UNKNOWN_IDENTITY_CONTENT).build();

            // Act & Assert
            assertRejected(() -> commandService.createPlanner(testUser.getId(), deviceId, unknownIdentity))
                    .withViolation("UNKNOWN_IDENTITY");
            verify(plannerRepository, never()).insert(any());
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
            verify(plannerRepository, never()).insert(any());
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
            Planner planner = testPlanner(1L, false, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = UpsertRequests.resending(planner)
                    .category("10F")
                    .contentVersion(99)
                    .build();

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), request,
                    false))
                    .withViolation("UNKNOWN_VERSION")
                    .isInstanceOfSatisfying(PlannerValidationException.class,
                            exception -> assertThat(exception.getSubErrors()).isEqualTo(java.util.List.of(
                                    new ValidationError("UNKNOWN_VERSION", "",
                                            "No game data for content version 99"))));
        }

        @Test
        void upsertPlanner_WhenCategoryIsUnchanged_ValidatesTheContent() {
            Planner planner = testPlanner(1L, false, UNKNOWN_IDENTITY_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest retitled = UpsertRequests.resending(planner).title("Renamed").build();

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), retitled,
                    false))
                    .withViolation("UNKNOWN_IDENTITY");
        }

        @Test
        void upsertPlanner_WhenAPublishedPlannersCategoryChanges_AdmitsAtPublishAndStoresTheNormalizedContent()
                throws Exception {
            PlannerContentValidator renaming = validatorRenaming("{\"egoGift\":{\"rename\":{\"9247\":\"9002\"}}}");
            String stored = TestDataFactory.VALID_CONTENT.replace(
                    "\"observationGiftIds\":[]", "\"observationGiftIds\":[\"19247\"]");
            Planner planner = testPlanner(1L, true, stored);
            planner.getContent().setCategory("10F");
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            commandServiceWith(renaming).upsertPlanner(testUser.getId(), deviceId, planner.getId(),
                    UpsertRequests.resending(planner).category("5F").build(), false);

            JsonNode saved = new ObjectMapper().readTree(planner.getContentJson());
            assertThat(saved.path("observationGiftIds")).isEqualTo(new ObjectMapper().createArrayNode().add("19002"));
            assertThat(planner.getCategory()).isEqualTo("5F");
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
            Planner planner = testPlanner(1L, false, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(),
                    UpsertRequests.resending(planner).title("Locked First").build(), false);

            assertThat(plannerCalls()).startsWith("findOwnershipById", "lockContent", "findAggregateForOwner");
        }

        @Test
        void deletePlanner_WhenCalled_LocksTheContentRowBeforeLoadingIt() {
            Planner planner = testPlanner(1L, true);
            when(plannerRepository.findOwnershipById(planner.getId()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            TransactionSynchronizationManager.initSynchronization();

            commandService.deletePlanner(testUser.getId(), planner.getId());

            assertThat(plannerCalls()).startsWith("findOwnershipById", "lockContent", "findAggregateForOwner");
        }

        @Test
        void deletePlanner_WhenAnotherUserAsks_FailsWithoutTakingTheLock() {
            Planner planner = testPlanner(1L, true);
            when(plannerRepository.findOwnershipById(planner.getId()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));

            assertRejected(() -> commandService.deletePlanner(testUser.getId() + 1, planner.getId()))
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

            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void deletePlanner_WhenPublishedPlanner_UnpublishesFirst() {
            // Arrange
            Planner planner = testPlanner(1L, true);
            assertThat(planner.isPublished()).isTrue();

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.findOwnershipById(planner.getId()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            TransactionSynchronizationManager.initSynchronization();

            // Act
            commandService.deletePlanner(testUser.getId(), planner.getId());

            // Assert
            assertThat(planner.isPublished()).isFalse(); // Auto-unpublished
            assertThat(planner.getContent().getDeletedAt()).isNotNull(); // Then soft deleted
        }

        @Test
        void deletePlanner_WhenUnpublishedPlanner_NoPublishChange() {
            // Arrange
            Planner planner = testPlanner(1L, false);

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.findOwnershipById(planner.getId()))
                    .thenReturn(Optional.of(OwnershipRows.of(planner)));
            TransactionSynchronizationManager.initSynchronization();

            // Act
            commandService.deletePlanner(testUser.getId(), planner.getId());

            // Assert
            assertThat(planner.isPublished()).isFalse(); // Still unpublished
            assertThat(planner.getContent().getDeletedAt()).isNotNull();
        }
    }

    @Nested
    class PlannerLimitEdgeCaseTests {

        @Test
        void createPlanner_WhenAtMaxMinusOne_Succeeds() {
            // Arrange
            UpsertPlannerRequest request = UpsertRequests.draft().title("Test Planner").build();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) (MAX_PLANNERS_PER_USER - 1));
            when(plannerRepository.insert(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return PlannerContentLifecycle.asPersisted(planner);
            });

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

            verify(plannerRepository, never()).insert(any());
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
            verify(plannerRepository, never()).insert(any());
        }
    }

    @Nested
    class BanEnforcementTests {

        @Test
        void upsertPlanner_WhenBannedUser_IsNotBlockedByTheGuard() {
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            when(plannerRepository.insert(any(Planner.class)))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted(
                            (Planner) invocation.getArgument(0)));

            UpsertPlannerRequest request = UpsertRequests.draft().id(null).status(null).build();

            UUID plannerId = UUID.randomUUID();

            // A ban withdraws distribution (publish, comment), never possession.
            UpsertResult result = commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false);

            assertThat(result.isCreated()).as("a banned user must keep private planner work").isTrue();
            assertThat(result.response().id()).isEqualTo(plannerId);
        }

        @Test
        void upsertPlanner_WhenNonBannedUser_Succeeds() {
            // Arrange
            when(plannerRepository.countActiveByUserId(testUser.getId()))
                    .thenReturn(0L);
            when(plannerRepository.insert(any(Planner.class)))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted(
                            (Planner) invocation.getArgument(0)));

            UpsertPlannerRequest request = UpsertRequests.draft()
                    .id(null)
                    .title("Test Planner")
                    .status(null)
                    .build();

            UUID plannerId = UUID.randomUUID();

            // Act
            UpsertResult result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, false);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.isCreated()).isTrue();
            assertThat(result.response().id()).isEqualTo(plannerId);
            assertThat(result.response().title()).isEqualTo("Test Planner");
        }
    }

    @Nested
    class PublishedEditRestrictionTests {

        @Test
        void upsertPlanner_WhenBannedOwnerEditsAPublishedPlanner_RefusesWithUserBanned() {
            testUser.setBannedAt(Instant.now());
            testUser.setBannedBy(2L);
            Planner planner = testPlanner(1L, true, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest retitled = UpsertRequests.resending(planner).title("Renamed").build();

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), retitled,
                    false)).withCode("USER_BANNED");
            assertThat(planner.getTitle()).isEqualTo("Test Planner");
        }

        @Test
        void upsertPlanner_WhenTimedOutOwnerEditsAPublishedPlanner_RefusesBeforeTheSyncVersionCheck() {
            testUser.setTimeoutUntil(Instant.now().plusSeconds(3600));
            Planner planner = testPlanner(5L, true, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest stale = UpsertRequests.resending(planner).title("Renamed").syncVersion(1L).build();

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), stale,
                    false)).withCode("USER_TIMED_OUT");
            assertThat(restrictionCheck.decisions()).containsExactly(Decision.CHECKED);
        }

        @Test
        void upsertPlanner_WhenBannedOwnerEditsAPrivatePlanner_SavesAndRecordsAPrivateDecision() {
            testUser.setBannedAt(Instant.now());
            testUser.setBannedBy(2L);
            Planner planner = testPlanner(1L, false, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest retitled = UpsertRequests.resending(planner).title("Renamed").build();

            commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), retitled, false);

            assertThat(planner.getTitle()).isEqualTo("Renamed");
            assertThat(restrictionCheck.decisions()).containsExactly(Decision.PRIVATE);
        }

        @Test
        void upsertPlanner_WhenCreatingAPlanner_RecordsAPrivateDecision() {
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            when(plannerRepository.insert(any(Planner.class)))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted(
                            (Planner) invocation.getArgument(0)));

            commandService.upsertPlanner(testUser.getId(), deviceId, UUID.randomUUID(),
                    UpsertRequests.draft().id(null).status(null).build(), false);

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

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false))
                    .withCode("PLANNER_NOT_FOUND");
            verify(plannerRepository, never()).existsActiveById(any());
            verify(plannerRepository, never()).insert(any());
            verify(plannerRepository, never()).countActiveByUserId(any());
        }

        @Test
        void createOwnershipCheck_WhenOtherUserActive_ThrowsForbiddenWithoutAnExistenceProbe() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = UpsertRequests.draft().id(null).status(null).build();
            PlannerOwnershipRow ownership = ownership(testUser.getId() + 1, null);
            when(plannerRepository.findOwnershipById(plannerId))
                    .thenReturn(Optional.of(ownership));

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false))
                    .withCode("PLANNER_FORBIDDEN");
            verify(plannerRepository, never()).existsActiveById(any());
            verify(plannerRepository, never()).insert(any());
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
            when(plannerRepository.insert(any(Planner.class)))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted(
                            (Planner) invocation.getArgument(0)));

            UpsertResult result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, false);

            assertThat(result.isCreated()).isTrue();
            verify(plannerRepository, never()).existsActiveById(any());
        }
    }

    @Nested
    class KeywordDerivationTests {

        private static final String SINKING_CONTENT = TestDataFactory.withSelectedKeywords(
                TestDataFactory.VALID_CONTENT, java.util.Set.of("Sinking"));
        private static final String BURST_CONTENT = TestDataFactory.withSelectedKeywords(
                TestDataFactory.VALID_CONTENT, java.util.Set.of("Burst"));

        private Planner storedWithSinking() {
            Planner planner = testPlanner(3L, false);
            StoredPlannerContent.holding(planner.getContent(), SINKING_CONTENT);
            planner.getContent().setSelectedKeywords(java.util.Set.of("Sinking"));
            planner.getContent().setContentSchemaVersion(CURRENT_SCHEMA_VERSION);
            planner.getContent().setDeviceId(deviceId);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
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
            ArgumentCaptor<Planner> plannerCaptor = ArgumentCaptor.forClass(Planner.class);
            when(plannerRepository.insert(plannerCaptor.capture()))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted((Planner) invocation.getArgument(0)));

            commandService.createPlanner(testUser.getId(), deviceId, request);

            assertThat(plannerCaptor.getValue().getSelectedKeywords()).isEqualTo(java.util.Set.of("Sinking"));
        }

        @Test
        void upsertPlanner_WhenTopLevelKeywordsAreAbsent_DerivesTheColumnFromTheNewContent() {
            Planner planner = storedWithSinking();

            commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(),
                    UpsertRequests.resending(planner).content(BURST_CONTENT).build(), false);

            assertThat(planner.getSelectedKeywords()).isEqualTo(java.util.Set.of("Burst"));
        }

        @Test
        void upsertPlanner_WhenAStaleSaveDiffersOnlyInTopLevelKeywords_AcknowledgesWithoutConflict() {
            Planner planner = storedWithSinking();
            long storedVersion = planner.getSyncVersion();

            UpsertResult result = commandService.upsertPlanner(testUser.getId(), deviceId,
                    planner.getId(), UpsertRequests.resending(planner)
                            .content(SINKING_CONTENT)
                            .selectedKeywords(java.util.Set.of("Burst"))
                            .syncVersion(storedVersion - 1)
                            .build(),
                    false);

            assertThat(result.response().syncVersion()).isEqualTo(storedVersion);
            assertThat(planner.getSelectedKeywords()).isEqualTo(java.util.Set.of("Sinking"));
        }
    }
}
