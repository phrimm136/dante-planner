package org.danteplanner.backend.planner.service;
import org.danteplanner.backend.planner.dto.UpsertResult;
import org.danteplanner.backend.user.service.UsersApiAdapter;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.PlannerResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.precondition.PlannerCategoryValidator;
import org.danteplanner.backend.planner.precondition.PlannerLimitValidator;
import org.danteplanner.backend.planner.precondition.PlannerOwnershipValidator;
import org.danteplanner.backend.planner.sync.SyncVersionValidator;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerContentLifecycle;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.StoredPlannerContent;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.user.api.UserNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.planner.precondition.ContentVersionValidator;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.PlannerDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.danteplanner.backend.user.api.UserBannedException;

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
    private UserService userService;

    @Mock
    private PlannerContentValidator contentValidator;

    @Mock
    private ContentVersionValidator contentVersionValidator;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    private PlannerCommandService commandService;

    @Value("${planner.max-per-user}")
    private int maxPlannersPerUser;

    private static final int CURRENT_SCHEMA_VERSION = 2;

    private User testUser;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlannerAccessGuard accessGuard = new PlannerAccessGuard(new UsersApiAdapter(userService, null), plannerRepository);

        commandService = new PlannerCommandService(
                plannerRepository,
                statsRepository,
                contentValidator,
                contentVersionValidator,
                plannerCatalogService,
                accessGuard,
                new PlannerCategoryValidator(),
                new PlannerLimitValidator(),
                new PlannerOwnershipValidator(),
                new SyncVersionValidator(),
                new ObjectMapper(),
                maxPlannersPerUser,
                CURRENT_SCHEMA_VERSION
        );

        testUser = TestDataFactory.unsavedUser(1L);

        deviceId = UUID.randomUUID();

        when(userService.findById(testUser.getId())).thenReturn(testUser);
        when(contentValidator.parse(any(), any(), any(), any()))
                .thenAnswer(invocation -> TestDataFactory.document(invocation.getArgument(0)));
        when(contentValidator.stageOf(any())).thenCallRealMethod();
    }

    private UpsertPlannerRequest createValidRequest() {
        return new UpsertPlannerRequest(
                UUID.randomUUID().toString(),
                "5F",
                "Test Planner",
                PlannerStatus.DRAFT,
                TestDataFactory.VALID_CONTENT,
                6,
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
                        .build(), "{\"data\": \"test\"}")),
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
            when(userService.findById(testUser.getId())).thenReturn(testUser);
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
            when(userService.findById(nonExistentUserId)).thenThrow(new UserNotFoundException(nonExistentUserId));

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
            when(userService.findById(testUser.getId())).thenReturn(testUser);

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
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            // Only this exact content-and-category pair is rejected, so a validation call carrying
            // anything else leaves the stub unmatched and the create completes without throwing.
            doThrow(new PlannerValidationException("INVALID_CONTENT", "Rejected content"))
                    .when(contentValidator).parse(eq(request.content()), eq(MDCategory.F5), any(), eq(Stage.DRAFT));

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertEquals("INVALID_CONTENT", exception.getOriginalCode());
            verify(plannerRepository, never()).insert(any());
        }

        @Test
        void createPlanner_WhenInvalidContentVersion_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = withContentVersion(createValidRequest(), 5); // Old version
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            doThrow(new PlannerValidationException("INVALID_CONTENT_VERSION", "Invalid content version"))
                    .when(contentVersionValidator).validateVersionForCreate(any(), eq(5));

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertEquals("INVALID_CONTENT_VERSION", exception.getOriginalCode());
            verify(plannerRepository, never()).insert(any());
            verify(contentValidator, never()).parse(any(), any(), any(), any());
        }
    }

    @Nested
    class UpsertCategoryTests {

        private UpsertPlannerRequest resending(Planner planner, String category, String content) {
            return new UpsertPlannerRequest(planner.getId().toString(), category, planner.getTitle(), null,
                    content, planner.getContentVersion(), PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(), null);
        }

        private final String edited = TestDataFactory.withSelectedKeywords(TestDataFactory.VALID_CONTENT,
                java.util.Set.of("Burst"));

        @Test
        void upsertPlanner_WhenOnlyTheCategoryChanges_ChecksOnlyTheFloorRules() {
            Planner planner = createTestPlanner();
            String stored = planner.getContentJson();
            PlannerDocument readmitted = TestDataFactory.document(TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(contentValidator.reparseForCategory(stored, MDCategory.F10, Stage.DRAFT)).thenReturn(readmitted);

            UpsertResult result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, planner.getId(), resending(planner, "10F", stored), false);

            assertEquals("10F", result.response().category());
            assertEquals(readmitted.json(), planner.getContentJson());
            verify(contentValidator, never()).parse(any(), any(), any(), any());
            verify(contentValidator, never()).season(anyInt());
        }

        @Test
        void upsertPlanner_WhenCategoryAndContentChange_ValidatesTheContent() {
            Planner planner = createTestPlanner();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            commandService.upsertPlanner(
                    testUser.getId(), deviceId, planner.getId(), resending(planner, "10F", edited), false);

            verify(contentValidator).season(planner.getContentVersion());
            verify(contentValidator).parse(eq(edited), eq(MDCategory.F10), any(), eq(Stage.DRAFT));
            verify(contentValidator, never()).reparseForCategory(any(), any(), any());
        }

        @Test
        void upsertPlanner_WhenCategoryAndContentVersionChange_ValidatesTheContent() {
            Planner planner = createTestPlanner();
            String stored = planner.getContentJson();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            doReturn(TestDataFactory.document(TestDataFactory.VALID_CONTENT))
                    .when(contentValidator).parse(eq(stored), eq(MDCategory.F10), any(), eq(Stage.DRAFT));
            UpsertPlannerRequest request = withContentVersion(resending(planner, "10F", stored), 7);

            commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), request, false);

            verify(contentValidator).season(7);
            verify(contentValidator).parse(eq(stored), eq(MDCategory.F10), any(), eq(Stage.DRAFT));
            verify(contentValidator, never()).reparseForCategory(any(), any(), any());
        }

        @Test
        void upsertPlanner_WhenCategoryIsUnchanged_ValidatesTheContent() {
            Planner planner = createTestPlanner();
            String stored = planner.getContentJson();
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            doReturn(TestDataFactory.document(TestDataFactory.VALID_CONTENT))
                    .when(contentValidator).parse(eq(stored), eq(MDCategory.F5), any(), eq(Stage.DRAFT));
            UpsertPlannerRequest retitled = new UpsertPlannerRequest(planner.getId().toString(), "5F", "Renamed",
                    null, stored, planner.getContentVersion(), PlannerType.MIRROR_DUNGEON, planner.getSyncVersion(), null);

            commandService.upsertPlanner(testUser.getId(), deviceId, planner.getId(), retitled, false);

            verify(contentValidator).parse(eq(stored), eq(MDCategory.F5), any(), eq(Stage.DRAFT));
            verify(contentValidator, never()).reparseForCategory(any(), any(), any());
        }

        @Test
        void upsertPlanner_WhenAPublishedPlannersCategoryChanges_AdmitsAtPublishAndStoresTheNormalizedContent() {
            Planner planner = testPlanner(1L, true);
            String stored = planner.getContentJson();
            PlannerDocument normalized = TestDataFactory.document(TestDataFactory.VALID_CONTENT);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(contentValidator.reparseForCategory(stored, MDCategory.F10, Stage.PUBLISH)).thenReturn(normalized);

            commandService.upsertPlanner(
                    testUser.getId(), deviceId, planner.getId(), resending(planner, "10F", stored), false);

            assertEquals(normalized.json(), planner.getContentJson());
            assertEquals("10F", planner.getCategory());
        }

        @Test
        void upsertPlanner_WhenAPublishedPlannersContentChanges_ValidatesAtPublish() {
            Planner planner = testPlanner(1L, true);
            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            commandService.upsertPlanner(
                    testUser.getId(), deviceId, planner.getId(), resending(planner, "5F", edited), false);

            verify(contentValidator).parse(eq(edited), eq(MDCategory.F5), any(), eq(Stage.PUBLISH));
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
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> commandService.deletePlanner(testUser.getId(), plannerId)
            );

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
            when(userService.findById(testUser.getId())).thenReturn(testUser);
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
            assertThrows(
                    PlannerLimitExceededException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

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

            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);

            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    null, "5F", "Test Planner", null, "{}", 1, PlannerType.MIRROR_DUNGEON, null, null);

            UUID plannerId = UUID.randomUUID();

            // A ban withdraws distribution (publish, comment), never possession. Downstream mock
            // gaps may still fail the call; only the restriction verdict is under test here.
            UserBannedException blocked = null;
            try {
                commandService.upsertPlanner(1L, deviceId, plannerId, request, false);
            } catch (UserBannedException e) {
                blocked = e;
            } catch (RuntimeException ignored) {
                // unrelated to the restriction verdict
            }

            assertNull(blocked, "a banned user must keep private planner work");
        }

        @Test
        void upsertPlanner_WhenNonBannedUser_Succeeds() {
            // Arrange
            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);
            when(plannerRepository.countActiveByUserId(testUser.getId()))
                    .thenReturn(0L);
            when(plannerRepository.insert(any(Planner.class)))
                    .thenAnswer(invocation -> PlannerContentLifecycle.asPersisted(
                            (Planner) invocation.getArgument(0)));

            UpsertPlannerRequest request = new UpsertPlannerRequest(null, "5F", "Test Planner", null,
                    TestDataFactory.VALID_CONTENT, 1, PlannerType.MIRROR_DUNGEON, null, null);

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
    class UpsertSoftDeleteGuardTests {

        private UpsertPlannerRequest buildRequest() {
            UpsertPlannerRequest request = new UpsertPlannerRequest(null, "5F", "Test Planner", null,
                    TestDataFactory.VALID_CONTENT, 1, PlannerType.MIRROR_DUNGEON, null, null);
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

            assertThrows(
                    PlannerNotFoundException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
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

            assertThrows(
                    PlannerForbiddenException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
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
            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);
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
                    null, SINKING_CONTENT, 6, PlannerType.MIRROR_DUNGEON, null, java.util.Set.of("Burst"));
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
