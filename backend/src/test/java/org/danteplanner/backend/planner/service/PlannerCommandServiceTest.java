package org.danteplanner.backend.planner.service;
import org.danteplanner.backend.planner.dto.UpsertResult;
import org.danteplanner.backend.planner.dto.UpsertPlannerRequest;
import org.danteplanner.backend.planner.dto.UpdatePlannerRequest;
import org.danteplanner.backend.planner.dto.PlannerResponse;
import org.danteplanner.backend.planner.dto.ImportPlannersResponse;
import org.danteplanner.backend.planner.dto.ImportPlannersRequest;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.dto.*;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerClassification;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.planner.validation.ContentVersionValidator;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
import org.danteplanner.backend.planner.validation.ValidationPolicy;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlannerCommandService (owner CRUD write operations:
 * create/upsert/update/delete/import).
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PlannerCommandServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository statsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlannerSyncEventService sseService;

    @Mock
    private PlannerContentValidator contentValidator;

    @Mock
    private ContentVersionValidator contentVersionValidator;

    @Mock
    private PlannerCatalogService plannerCatalogService;

    private PlannerCommandService commandService;

    @Value("${planner.max-per-user}")
    private int maxPlannersPerUser;

    @Value("${planner.schema-version}")
    private int currentSchemaVersion;

    private User testUser;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        PlannerAccessGuard accessGuard = new PlannerAccessGuard(userRepository, plannerRepository);

        commandService = new PlannerCommandService(
                plannerRepository,
                statsRepository,
                sseService,
                contentValidator,
                contentVersionValidator,
                plannerCatalogService,
                accessGuard,
                maxPlannersPerUser,
                currentSchemaVersion
        );

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();

        deviceId = UUID.randomUUID();

        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
    }

    private UpsertPlannerRequest createValidRequest() {
        return new UpsertPlannerRequest(
                UUID.randomUUID().toString(),
                "5F",
                "Test Planner",
                PlannerStatus.DRAFT,
                "{\"data\": \"test\"}",
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

    private Planner testPlanner(long syncVersion, boolean published) {
        Planner planner = Planner.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .build();
        planner.attach(
                PlannerContent.builder()
                        .title("Test Planner")
                        .category("5F")
                        .status(PlannerStatus.DRAFT)
                        .content("{\"data\": \"test\"}")
                        .contentSchemaVersion(1)
                        .gameContentVersion(6)
                        .syncVersion(syncVersion)
                        .lastModifiedAt(Instant.now())
                        .build(),
                PlannerPublication.builder().published(published).build(),
                PlannerModeration.builder().build());
        return planner;
    }

    private Planner createTestPlanner() {
        return testPlanner(1L, false);
    }

    @Nested
    @DisplayName("createPlanner Tests")
    class CreatePlannerTests {

        @Test
        @DisplayName("Should create planner successfully when within limit")
        void createPlanner_WhenWithinLimit_Succeeds() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(50L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            PlannerResponse response = commandService.createPlanner(testUser.getId(), deviceId, request);

            // Assert
            assertNotNull(response);
            assertEquals("Test Planner", response.title());
            assertEquals("5F", response.category());
            assertEquals(1L, response.syncVersion());
            // The fan-out has no state form at this tier; observing the delivered event means a real
            // PlannerSyncEventService with a subscribed emitter.
            verify(sseService).notifyPlannerUpdate(eq(testUser.getId()), eq(deviceId), any(UUID.class), eq(SseEventType.CREATED), eq(response));
        }

        @Test
        @DisplayName("Should throw PlannerLimitExceededException when at max planners")
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
            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found")
        void createPlanner_WhenUserNotFound_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            Long nonExistentUserId = 999L;
            when(plannerRepository.countActiveByUserId(nonExistentUserId)).thenReturn(0L);
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));
            when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> commandService.createPlanner(nonExistentUserId, deviceId, request)
            );

            assertEquals(nonExistentUserId, exception.getUserId());
            assertTrue(exception.getMessage().contains(nonExistentUserId.toString()));
            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should use default title when not provided")
        void createPlanner_WhenNoTitle_UsesDefault() {
            // Arrange
            UpsertPlannerRequest request = withTitle(createValidRequest(), null);
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));

            ArgumentCaptor<Planner> plannerCaptor = ArgumentCaptor.forClass(Planner.class);
            when(plannerRepository.save(plannerCaptor.capture())).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            commandService.createPlanner(testUser.getId(), deviceId, request);

            // Assert
            assertEquals("Untitled", plannerCaptor.getValue().getTitle());
        }

        @Test
        @DisplayName("Should call content validator before saving")
        void createPlanner_WhenCalled_ValidatesContentBeforeSave() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(0L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            // Only this exact content-and-category pair is rejected, so a validation call carrying
            // anything else leaves the stub unmatched and the create completes without throwing.
            when(contentValidator.validate(request.content(), request.category()))
                    .thenThrow(new PlannerValidationException("INVALID_CONTENT", "Rejected content"));

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertEquals("INVALID_CONTENT", exception.getErrorCode());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerValidationException when content version is invalid")
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

            assertEquals("INVALID_CONTENT_VERSION", exception.getErrorCode());
            verify(plannerRepository, never()).save(any());
            verify(contentValidator, never()).validate(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("updatePlanner Tests")
    class UpdatePlannerTests {

        @Test
        @DisplayName("Should increment syncVersion on successful update")
        void updatePlanner_WhenSuccess_IncrementsSyncVersion() {
            // Arrange
            Planner planner = testPlanner(5L, false);

            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    "Updated Title", null, null, null, 5L, null);

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse response = commandService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false);

            // Assert
            assertEquals(6L, response.syncVersion());
            assertEquals("Updated Title", response.title());
            // The fan-out has no state form at this tier; observing the delivered event means a real
            // PlannerSyncEventService with a subscribed emitter.
            verify(sseService).notifyPlannerUpdate(testUser.getId(), deviceId, planner.getId(), SseEventType.UPDATED, response);
        }

        @Test
        @DisplayName("Should throw PlannerConflictException on version mismatch")
        void updatePlanner_WhenVersionMismatch_ThrowsException() {
            // Arrange
            Planner planner = testPlanner(5L, false);

            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    "Updated Title", null, null, null, 3L, null); // Wrong version

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            // Act & Assert
            PlannerConflictException exception = assertThrows(
                    PlannerConflictException.class,
                    () -> commandService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false)
            );

            assertEquals(5L, exception.getActualVersion());
            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void updatePlanner_WhenNotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    null, null, null, null, 1L, null);

            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> commandService.updatePlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
        }

        @Test
        @DisplayName("Should validate content on update when provided")
        void updatePlanner_WhenContent_ValidatesContent() {
            // Arrange
            Planner planner = createTestPlanner();
            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    null, null, null, "{\"updated\": \"content\"}", planner.getSyncVersion(), null);

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            // A request without a category must validate against the planner's own. Only that exact
            // triple is rejected, so any other combination leaves the stub unmatched and succeeds.
            when(contentValidator.validate(request.content(), planner.getCategory(),
                    ValidationPolicy.forPublicationState(planner.getPublished())))
                    .thenThrow(new PlannerValidationException("INVALID_CONTENT", "Rejected content"));

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> commandService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false)
            );

            assertEquals("INVALID_CONTENT", exception.getErrorCode());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should only update provided fields")
        void updatePlanner_WhenPartialUpdate_OnlyUpdatesProvidedFields() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.getContent().setTitle("Original Title");
            planner.getContent().setStatus(PlannerStatus.DRAFT);

            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    "New Title", null, null, null, planner.getSyncVersion(), null);
            // status not provided

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse response = commandService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false);

            // Assert
            assertEquals("New Title", response.title());
            assertEquals(PlannerStatus.DRAFT, response.status()); // Original status preserved
        }
    }

    @Nested
    @DisplayName("deletePlanner Tests")
    class DeletePlannerTests {

        @Test
        @DisplayName("Should throw PlannerNotFoundException when not found")
        void deletePlanner_WhenNotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> commandService.deletePlanner(testUser.getId(), deviceId, plannerId)
            );

            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should auto-unpublish published planner before deletion")
        void deletePlanner_WhenPublishedPlanner_UnpublishesFirst() {
            // Arrange
            Planner planner = testPlanner(1L, true);
            assertTrue(planner.getPublished());

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            commandService.deletePlanner(testUser.getId(), deviceId, planner.getId());

            // Assert
            assertFalse(planner.getPublished()); // Auto-unpublished
            assertNotNull(planner.getContent().getDeletedAt()); // Then soft deleted
            verify(plannerRepository).save(planner);
        }

        @Test
        @DisplayName("Should not change unpublished planner on delete")
        void deletePlanner_WhenUnpublishedPlanner_NoPublishChange() {
            // Arrange
            Planner planner = testPlanner(1L, false);

            when(plannerRepository.findAggregateForOwner(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            commandService.deletePlanner(testUser.getId(), deviceId, planner.getId());

            // Assert
            assertFalse(planner.getPublished()); // Still unpublished
            assertNotNull(planner.getContent().getDeletedAt());
        }
    }

    @Nested
    @DisplayName("importPlanners Tests")
    class ImportPlannersTests {

        @Test
        @DisplayName("Should import planners successfully when within limit")
        void importPlanners_WhenWithinLimit_Succeeds() {
            // Arrange
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn(50L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            List<UpsertPlannerRequest> requests = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                UpsertPlannerRequest req = createValidRequest();
                req = withTitle(req, "Imported " + i);
                requests.add(req);
            }

            ImportPlannersRequest importRequest = new ImportPlannersRequest(requests);

            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            ImportPlannersResponse response = commandService.importPlanners(testUser.getId(), importRequest);

            // Assert
            assertEquals(3, response.imported());
            assertEquals(3, response.total());
            assertEquals(3, response.planners().size());
            assertEquals(List.of("Imported 0", "Imported 1", "Imported 2"),
                    response.planners().stream().map(PlannerSummaryResponse::title).toList());
            // Validation of a batch member leaves no state behind on the success path; proving each
            // one was screened as an outcome needs a rejected member and a real validator.
            verify(contentValidator, times(3)).validate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should reject import when would exceed limit")
        void importPlanners_WhenExceedsLimit_ThrowsException() {
            // Arrange
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) (maxPlannersPerUser - 2));

            List<UpsertPlannerRequest> requests = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                requests.add(createValidRequest());
            }

            ImportPlannersRequest importRequest = new ImportPlannersRequest(requests);

            // Act & Assert
            assertThrows(
                    PlannerLimitExceededException.class,
                    () -> commandService.importPlanners(testUser.getId(), importRequest)
            );

            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found during import")
        void importPlanners_WhenUserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentUserId = 999L;
            when(plannerRepository.countActiveByUserId(nonExistentUserId)).thenReturn(0L);
            when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

            List<UpsertPlannerRequest> requests = new ArrayList<>();
            requests.add(createValidRequest());

            ImportPlannersRequest importRequest = new ImportPlannersRequest(requests);

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> commandService.importPlanners(nonExistentUserId, importRequest)
            );

            assertEquals(nonExistentUserId, exception.getUserId());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow import up to exactly max planners")
        void importPlanners_WhenExactlyToLimit_Success() {
            // Arrange
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) (maxPlannersPerUser - 5));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            List<UpsertPlannerRequest> requests = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                requests.add(createValidRequest());
            }

            ImportPlannersRequest importRequest = new ImportPlannersRequest(requests);

            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            ImportPlannersResponse response = commandService.importPlanners(testUser.getId(), importRequest);

            // Assert
            assertEquals(5, response.imported());
            assertEquals(5, response.total());
            assertEquals(5, response.planners().size());
        }
    }

    @Nested
    @DisplayName("Planner Limit Edge Cases")
    class PlannerLimitEdgeCaseTests {

        @Test
        @DisplayName("Should allow creating planner when at max-1")
        void createPlanner_WhenAtMaxMinusOne_Succeeds() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) (maxPlannersPerUser - 1));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.getContent().setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act & Assert - should not throw
            PlannerResponse response = assertDoesNotThrow(
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request));

            assertEquals(UUID.fromString(request.id()), response.id());
            assertEquals("Test Planner", response.title());
            assertEquals(1L, response.syncVersion());
        }

        @Test
        @DisplayName("Should fail creating planner when at max")
        void createPlanner_WhenAtMax_ThrowsLimitExceeded() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countActiveByUserId(testUser.getId())).thenReturn((long) maxPlannersPerUser);

            // Act & Assert
            assertThrows(
                    PlannerLimitExceededException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should count only non-deleted planners for limit")
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
            verify(plannerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Ban Enforcement Tests")
    class BanEnforcementTests {

        @Test
        @DisplayName("A ban does not block private planner work")
        void upsertPlanner_WhenBannedUser_IsNotBlockedByTheGuard() {
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);

            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));

            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    null, "5F", "Test Planner", null, "{}", 1, PlannerType.MIRROR_DUNGEON, null, null);

            UUID plannerId = UUID.randomUUID();

            // A ban withdraws distribution (publish, comment), never possession. Downstream mock
            // gaps may still fail the call; only the restriction verdict is under test here.
            org.danteplanner.backend.user.exception.UserBannedException blocked = null;
            try {
                commandService.upsertPlanner(1L, deviceId, plannerId, request, false);
            } catch (org.danteplanner.backend.user.exception.UserBannedException e) {
                blocked = e;
            } catch (RuntimeException ignored) {
                // unrelated to the restriction verdict
            }

            assertNull(blocked, "a banned user must keep private planner work");
        }

        @Test
        @DisplayName("Non-banned user can upsert planner")
        void upsertPlanner_WhenNonBannedUser_Succeeds() {
            // Arrange
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));
            when(plannerRepository.countActiveByUserId(testUser.getId()))
                    .thenReturn(0L);
            when(plannerRepository.save(any(Planner.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    null, "5F", "Test Planner", null, "{}", 1, PlannerType.MIRROR_DUNGEON, null, null);

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
    @DisplayName("Upsert Soft-Delete Guard Tests")
    class UpsertSoftDeleteGuardTests {

        private UpsertPlannerRequest buildRequest() {
            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    null, "5F", "Test Planner", null, "{}", 1, PlannerType.MIRROR_DUNGEON, null, null);
            return request;
        }

        @Test
        @DisplayName("create classification: an owner's soft-deleted planner throws PlannerNotFoundException from one SELECT")
        void createExistenceTwoSelects_WhenOwnSoftDeleted_ThrowsNotFound() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = buildRequest();
            PlannerClassification classification = mock(PlannerClassification.class);
            lenient().when(classification.getUserId()).thenReturn(testUser.getId());
            lenient().when(classification.getDeletedAt()).thenReturn(Instant.now());
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            lenient().when(plannerRepository.findClassificationById(plannerId))
                    .thenReturn(Optional.of(classification));

            assertThrows(
                    PlannerNotFoundException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
            verify(plannerRepository, never()).existsByIdAndUserId(any(), any());
            verify(plannerRepository, never()).existsActiveById(any());
            verify(plannerRepository, never()).save(any());
            verify(plannerRepository, never()).countActiveByUserId(any());
        }

        @Test
        @DisplayName("create classification: another user's active planner throws PlannerForbiddenException from one SELECT")
        void createExistenceTwoSelects_WhenOtherUserActive_ThrowsForbidden() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = buildRequest();
            PlannerClassification classification = mock(PlannerClassification.class);
            lenient().when(classification.getUserId()).thenReturn(testUser.getId() + 1);
            lenient().when(classification.getDeletedAt()).thenReturn(null);
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            lenient().when(plannerRepository.findClassificationById(plannerId))
                    .thenReturn(Optional.of(classification));

            assertThrows(
                    PlannerForbiddenException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
            verify(plannerRepository, never()).existsByIdAndUserId(any(), any());
            verify(plannerRepository, never()).existsActiveById(any());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("create classification: a genuinely new id proceeds to create")
        void createExistenceTwoSelects_WhenGenuinelyNew_Creates() {
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = buildRequest();
            when(plannerRepository.findAggregateForOwner(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            lenient().when(plannerRepository.findClassificationById(plannerId))
                    .thenReturn(Optional.empty());
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));
            when(plannerRepository.countActiveByUserId(testUser.getId()))
                    .thenReturn(0L);
            when(plannerRepository.save(any(Planner.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpsertResult result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, false);

            assertTrue(result.isCreated());
            verify(plannerRepository, never()).existsByIdAndUserId(any(), any());
            verify(plannerRepository, never()).existsActiveById(any());
        }
    }
}
