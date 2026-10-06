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
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerContentLifecycle;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.StoredPlannerContent;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.gamedata.GameDataLoader;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.exception.PlannerValidationException.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Unit tests for PlannerCommandService (owner CRUD write operations:
 * create/upsert/update/delete).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PlannerCommandServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    private PlannerCommandService commandService;

    @Value("${planner.max-per-user}")
    private int maxPlannersPerUser;

    private static final int CURRENT_SCHEMA_VERSION = 2;

    private static final int CURRENT_MD_VERSION =
            TestDataFactory.gameDataRegistry().plannerVersions().mdCurrentVersion();

    private static final String UNKNOWN_IDENTITY_CONTENT = TestDataFactory.VALID_CONTENT.replace(
            "\"identity\":{\"id\":\"10101\"", "\"identity\":{\"id\":\"10199\"");

    private final FakeUsers users = new FakeUsers();

    private User testUser;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

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
                plannerCatalogService,
                new PlannerAccessGuard(users, plannerRepository),
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                new ObjectMapper(),
                maxPlannersPerUser,
                CURRENT_SCHEMA_VERSION
        );
    }

    private UpsertPlannerRequest createValidRequest() {
        return new UpsertPlannerRequest(
                UUID.randomUUID().toString(),
                "5F",
                "Test Planner",
                PlannerStatus.DRAFT,
                TestDataFactory.VALID_CONTENT,
                CURRENT_MD_VERSION,
                PlannerType.MIRROR_DUNGEON,
                null,
                null);
    }

    private UpsertPlannerRequest withTitle(UpsertPlannerRequest r, String title) {
        return new UpsertPlannerRequest(r.id(), r.category(), title, r.status(),
                r.content(), r.contentVersion(), r.plannerType(), r.syncVersion(), r.selectedKeywords());
    }

    private UpsertPlannerRequest withContentVersion(UpsertPlannerRequest r, Integer contentVersion) {
        return new UpsertPlannerRequest(r.id(), r.category(), r.title(), r.status(),
                r.content(), contentVersion, r.plannerType(), r.syncVersion(), r.selectedKeywords());
    }

    private UpsertPlannerRequest withContent(UpsertPlannerRequest r, String content) {
        return new UpsertPlannerRequest(r.id(), r.category(), r.title(), r.status(),
                content, r.contentVersion(), r.plannerType(), r.syncVersion(), r.selectedKeywords());
    }

    private Planner testPlanner(long syncVersion, boolean published) {
        return testPlanner(syncVersion, published, "{\"data\": \"test\"}");
    }

    private Planner testPlanner(long syncVersion, boolean published, String content) {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .userId(testUser.getId())
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .build();
        planner.attach(
                PlannerContentLifecycle.asPersisted(StoredPlannerContent.holding(PlannerContent.builder()
                        .title("Test Planner")
                        .category("5F")
                        .status(PlannerStatus.DRAFT)
                        .contentSchemaVersion(1)
                        .gameContentVersion(6)
                        .syncVersion(syncVersion)
                        .lastModifiedAt(Instant.now())
                        .build(), content)),
                PlannerPublication.builder().published(published).build(),
                PlannerModeration.builder().build());
        return planner;
    }

    private Planner createTestPlanner() {
        return testPlanner(1L, false);
    }

    @Nested
    class CreatePlannerTests {

        @Test
        void createPlanner_WhenWithinLimit_Succeeds() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
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
            assertNotNull(response);
            assertEquals("Test Planner", response.title());
            assertEquals("5F", response.category());
            assertEquals(1L, response.syncVersion());
        }

        @Test
        void createPlanner_WhenAtLimit_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) maxPlannersPerUser);

            // Act & Assert
            PlannerLimitExceededException exception = assertThrows(
                    PlannerLimitExceededException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertTrue(exception.getMessage().contains(String.valueOf(maxPlannersPerUser)));
            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void createPlanner_WhenUserNotFound_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            Long nonExistentUserId = 999L;
            when(plannerRepository.countActiveByUserId(nonExistentUserId)).thenReturn(0L);

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> commandService.createPlanner(nonExistentUserId, deviceId, request)
            );

            assertEquals(nonExistentUserId, exception.getUserId());
            assertTrue(exception.getMessage().contains(nonExistentUserId.toString()));
            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void createPlanner_WhenNoTitle_UsesDefault() {
            // Arrange
            UpsertPlannerRequest request = withTitle(createValidRequest(), null);
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
            assertEquals("Untitled", plannerCaptor.getValue().getTitle());
        }

        @Test
        void createPlanner_WhenCalled_ValidatesContentBeforeSave() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            UpsertPlannerRequest unknownIdentity = withContent(request, UNKNOWN_IDENTITY_CONTENT);

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, unknownIdentity)
            );

            assertTrue(exception.getSubErrors().stream().map(ValidationError::code).toList()
                    .contains("UNKNOWN_IDENTITY"));
            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void createPlanner_WhenInvalidContentVersion_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = withContent(
                    withContentVersion(createValidRequest(), 5), "{\"equipment\":"); // Old version
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertEquals("INVALID_CONTENT_VERSION", exception.getOriginalCode());
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

        private UpsertPlannerRequest resending(Planner planner, String category, String content) {
            return new UpsertPlannerRequest(planner.getId().toString(), category, planner.getTitle(), null,
                    content, planner.getContentVersion(), PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(), null);
        }

        @Test
        void upsertPlanner_WhenCategoryAndContentVersionChange_ValidatesTheContent() {
            Planner planner = testPlanner(1L, false, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest request = withContentVersion(
                    resending(planner, "10F", TestDataFactory.VALID_CONTENT), 99);

            PlannerValidationException exception = assertThrows(PlannerValidationException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), request, false));

            assertEquals(java.util.List.of(new ValidationError("UNKNOWN_VERSION", "",
                    "No game data for content version 99")), exception.getSubErrors());
        }

        @Test
        void upsertPlanner_WhenCategoryIsUnchanged_ValidatesTheContent() {
            Planner planner = testPlanner(1L, false, UNKNOWN_IDENTITY_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            UpsertPlannerRequest retitled = new UpsertPlannerRequest(planner.getId().toString(), "5F", "Renamed",
                    null, UNKNOWN_IDENTITY_CONTENT, planner.getContentVersion(), PlannerType.MIRROR_DUNGEON,
                    planner.getSyncVersion(), null);

            PlannerValidationException exception = assertThrows(PlannerValidationException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), retitled, false));

            assertTrue(exception.getSubErrors().stream().map(ValidationError::code).toList()
                    .contains("UNKNOWN_IDENTITY"));
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
                    resending(planner, "5F", stored), false);

            JsonNode saved = new ObjectMapper().readTree(planner.getContentJson());
            assertEquals(new ObjectMapper().createArrayNode().add("19002"), saved.path("observationGiftIds"));
            assertEquals("5F", planner.getCategory());
        }

        @Test
        void upsertPlanner_WhenAPublishedPlannersContentChanges_ValidatesAtPublish() {
            Planner planner = testPlanner(1L, true, TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            String unknownKeyword = TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT,
                    java.util.List.of("Nope"));

            PlannerValidationException exception = assertThrows(PlannerValidationException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(),
                            resending(planner, "5F", unknownKeyword), false));

            assertEquals("UNKNOWN_KEYWORD", exception.getOriginalCode());
        }
    }

    @Nested
    class DeletePlannerTests {

        @Test
        void deletePlanner_WhenNotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> commandService.deletePlanner(testUser.getId(), plannerId))
                    .withCode("PLANNER_NOT_FOUND");

            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void deletePlanner_WhenPublishedPlanner_UnpublishesFirst() {
            // Arrange
            Planner planner = testPlanner(1L, true);
            assertTrue(planner.isPublished());

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            // Act
            commandService.deletePlanner(testUser.getId(), planner.getId());

            // Assert
            assertFalse(planner.isPublished()); // Auto-unpublished
            assertNotNull(planner.getContent().getDeletedAt()); // Then soft deleted
        }

        @Test
        void deletePlanner_WhenUnpublishedPlanner_NoPublishChange() {
            // Arrange
            Planner planner = testPlanner(1L, false);

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            // Act
            commandService.deletePlanner(testUser.getId(), planner.getId());

            // Assert
            assertFalse(planner.isPublished()); // Still unpublished
            assertNotNull(planner.getContent().getDeletedAt());
        }
    }

    @Nested
    class PlannerLimitEdgeCaseTests {

        @Test
        void createPlanner_WhenAtMaxMinusOne_Succeeds() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) (maxPlannersPerUser - 1));
            when(plannerRepository.insert(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return PlannerContentLifecycle.asPersisted(planner);
            });

            // Act & Assert - should not throw
            PlannerResponse response = assertDoesNotThrow(
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request));

            assertEquals(UUID.fromString(request.id()), response.id());
            assertEquals("Test Planner", response.title());
            assertEquals(1L, response.syncVersion());
        }

        @Test
        void createPlanner_WhenAtMax_ThrowsLimitExceeded() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) maxPlannersPerUser);

            // Act & Assert
            assertRejected(() -> commandService.createPlanner(testUser.getId(), deviceId, request))
                    .withCode("PLANNER_LIMIT_EXCEEDED");

            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void createPlanner_WhenCheckingLimit_CountsOnlyNonDeleted() {
            UpsertPlannerRequest request = createValidRequest();
            // Only the deleted-excluding count is stubbed, and with a value no other source could
            // supply, so the verdict can only carry it if that count is what the limit reads.
            long activeCount = maxPlannersPerUser + 7L;
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(activeCount);

            PlannerLimitExceededException exception = assertThrows(
                    PlannerLimitExceededException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertTrue(exception.getMessage().contains(String.valueOf(activeCount)));
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

            UpsertPlannerRequest request = new UpsertPlannerRequest(null, "5F", "Test Planner", null,
                    TestDataFactory.VALID_CONTENT, CURRENT_MD_VERSION, PlannerType.MIRROR_DUNGEON, null, null);

            UUID plannerId = UUID.randomUUID();

            // A ban withdraws distribution (publish, comment), never possession.
            UpsertResult result = commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false);

            assertTrue(result.isCreated(), "a banned user must keep private planner work");
            assertEquals(plannerId, result.response().id());
        }

        @Test
        void upsertPlanner_WhenNonBannedUser_Succeeds() {
            // Arrange
            when(plannerRepository.countActiveByUserId(testUser.getId()))
                    .thenReturn(0L);
            when(plannerRepository.insert(any(Planner.class)))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted(
                            (Planner) invocation.getArgument(0)));

            UpsertPlannerRequest request = new UpsertPlannerRequest(null, "5F", "Test Planner", null,
                    TestDataFactory.VALID_CONTENT, CURRENT_MD_VERSION, PlannerType.MIRROR_DUNGEON, null, null);

            UUID plannerId = UUID.randomUUID();

            // Act
            UpsertResult result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, false);

            // Assert
            assertNotNull(result);
            assertTrue(result.isCreated());
            assertEquals(plannerId, result.response().id());
            assertEquals("Test Planner", result.response().title());
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
            UpsertPlannerRequest retitled = new UpsertPlannerRequest(planner.getId().toString(), "5F", "Renamed",
                    null, TestDataFactory.VALID_CONTENT, planner.getContentVersion(), PlannerType.MIRROR_DUNGEON,
                    planner.getSyncVersion(), null);

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), retitled,
                    false)).withCode("USER_BANNED");
            assertEquals("Test Planner", planner.getTitle());
        }
    }

    @Nested
    class UpsertSoftDeleteGuardTests {

        private UpsertPlannerRequest buildRequest() {
            UpsertPlannerRequest request = new UpsertPlannerRequest(null, "5F", "Test Planner", null,
                    TestDataFactory.VALID_CONTENT, CURRENT_MD_VERSION, PlannerType.MIRROR_DUNGEON, null, null);
            return request;
        }

        @Test
        void createOwnershipCheck_WhenOwnSoftDeleted_ThrowsNotFoundWithoutAnExistenceProbe() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = buildRequest();
            PlannerOwnershipRow ownership = mock(PlannerOwnershipRow.class);
            lenient().when(ownership.getUserId()).thenReturn(testUser.getId());
            lenient().when(ownership.getDeletedAt()).thenReturn(Instant.now());
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            lenient().when(plannerRepository.findOwnershipById(plannerId))
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
            UpsertPlannerRequest request = buildRequest();
            PlannerOwnershipRow ownership = mock(PlannerOwnershipRow.class);
            lenient().when(ownership.getUserId()).thenReturn(testUser.getId() + 1);
            lenient().when(ownership.getDeletedAt()).thenReturn(null);
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            lenient().when(plannerRepository.findOwnershipById(plannerId))
                    .thenReturn(Optional.of(ownership));

            assertRejected(() -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false))
                    .withCode("PLANNER_FORBIDDEN");
            verify(plannerRepository, never()).existsActiveById(any());
            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void createOwnershipCheck_WhenGenuinelyNew_CreatesWithoutAnExistenceProbe() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = buildRequest();
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            lenient().when(plannerRepository.findOwnershipById(plannerId))
                    .thenReturn(Optional.empty());
            when(plannerRepository.countActiveByUserId(testUser.getId()))
                    .thenReturn(0L);
            when(plannerRepository.insert(any(Planner.class)))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted(
                            (Planner) invocation.getArgument(0)));

            UpsertResult result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, false);

            assertTrue(result.isCreated());
            verify(plannerRepository, never()).existsActiveById(any());
        }
    }

    @Nested
    class KeywordDerivationTests {

        private static final String SINKING_CONTENT = TestDataFactory.withSelectedKeywords(
                TestDataFactory.VALID_CONTENT, java.util.Set.of("Sinking"));
        private static final String BURST_CONTENT = TestDataFactory.withSelectedKeywords(
                TestDataFactory.VALID_CONTENT, java.util.Set.of("Burst"));

        private UpsertPlannerRequest carrying(Planner planner, String content, java.util.Set<String> topLevel,
                Long syncVersion) {
            return new UpsertPlannerRequest(planner.getId().toString(), planner.getCategory(), planner.getTitle(),
                    null, content, planner.getContentVersion(), PlannerType.MIRROR_DUNGEON, syncVersion, topLevel);
        }

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
            UpsertPlannerRequest request = new UpsertPlannerRequest(UUID.randomUUID().toString(), "5F", "Test Planner",
                    null, SINKING_CONTENT, CURRENT_MD_VERSION, PlannerType.MIRROR_DUNGEON, null, java.util.Set.of("Burst"));
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            ArgumentCaptor<Planner> plannerCaptor = ArgumentCaptor.forClass(Planner.class);
            when(plannerRepository.insert(plannerCaptor.capture()))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted((Planner) invocation.getArgument(0)));

            commandService.createPlanner(testUser.getId(), deviceId, request);

            assertEquals(java.util.Set.of("Sinking"), plannerCaptor.getValue().getSelectedKeywords());
        }

        @Test
        void upsertPlanner_WhenTopLevelKeywordsAreAbsent_DerivesTheColumnFromTheNewContent() {
            Planner planner = storedWithSinking();

            commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(),
                    carrying(planner, BURST_CONTENT, null, planner.getSyncVersion()), false);

            assertEquals(java.util.Set.of("Burst"), planner.getSelectedKeywords());
        }

        @Test
        void upsertPlanner_WhenAStaleSaveDiffersOnlyInTopLevelKeywords_AcknowledgesWithoutConflict() {
            Planner planner = storedWithSinking();
            long storedVersion = planner.getSyncVersion();

            UpsertResult result = assertDoesNotThrow(() -> commandService.upsertPlanner(testUser.getId(), deviceId,
                    planner.getId(), carrying(planner, SINKING_CONTENT, java.util.Set.of("Burst"), storedVersion - 1),
                    false));

            assertEquals(storedVersion, result.response().syncVersion());
            assertEquals(java.util.Set.of("Sinking"), planner.getSelectedKeywords());
        }
    }
}
