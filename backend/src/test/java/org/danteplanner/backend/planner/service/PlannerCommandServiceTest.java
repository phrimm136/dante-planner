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
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerConflictException;
import org.danteplanner.backend.planner.exception.PlannerLimitExceededException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.user.exception.UserNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.planner.validation.ContentVersionValidator;
import org.danteplanner.backend.planner.validation.PlannerContentValidator;
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
    private UserRepository userRepository;

    @Mock
    private PlannerSyncEventService sseService;

    @Mock
    private PlannerContentValidator contentValidator;

    @Mock
    private ContentVersionValidator contentVersionValidator;

    @Mock
    private PlannerIndexService plannerIndexService;

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
                sseService,
                contentValidator,
                contentVersionValidator,
                plannerIndexService,
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

    private Planner.PlannerBuilder testPlannerBuilder() {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Test Planner")
                .category("5F")
                .status(PlannerStatus.DRAFT)
                .content("{\"data\": \"test\"}")
                .syncVersion(1L)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .savedAt(Instant.now());
    }

    private Planner createTestPlanner() {
        return testPlannerBuilder().build();
    }

    @Nested
    @DisplayName("isValidCategory Tests")
    class IsValidCategoryTests {

        @Test
        @DisplayName("Should return true for valid MD category with MIRROR_DUNGEON type")
        void isValidCategory_ValidMdCategory_ReturnsTrue() {
            assertTrue(commandService.isValidCategory(PlannerType.MIRROR_DUNGEON, "5F"));
            assertTrue(commandService.isValidCategory(PlannerType.MIRROR_DUNGEON, "10F"));
            assertTrue(commandService.isValidCategory(PlannerType.MIRROR_DUNGEON, "15F"));
        }

        @Test
        @DisplayName("Should return true for valid RR category with REFRACTED_RAILWAY type")
        void isValidCategory_ValidRrCategory_ReturnsTrue() {
            assertTrue(commandService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER"));
        }

        @Test
        @DisplayName("Should return false for invalid category")
        void isValidCategory_InvalidCategory_ReturnsFalse() {
            assertFalse(commandService.isValidCategory(PlannerType.MIRROR_DUNGEON, "INVALID"));
            assertFalse(commandService.isValidCategory(PlannerType.MIRROR_DUNGEON, ""));
            assertFalse(commandService.isValidCategory(PlannerType.MIRROR_DUNGEON, null));
        }

        @Test
        @DisplayName("Should return false when MD category used with RR type")
        void isValidCategory_MdCategoryWithRrType_ReturnsFalse() {
            assertFalse(commandService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "5F"));
            assertFalse(commandService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "10F"));
            assertFalse(commandService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "15F"));
        }

        @Test
        @DisplayName("Should return false when RR category used with MD type")
        void isValidCategory_RrCategoryWithMdType_ReturnsFalse() {
            assertFalse(commandService.isValidCategory(PlannerType.MIRROR_DUNGEON, "RR_PLACEHOLDER"));
        }
    }

    @Nested
    @DisplayName("createPlanner Tests")
    class CreatePlannerTests {

        @Test
        @DisplayName("Should create planner successfully when within limit")
        void createPlanner_WithinLimit_Success() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(50L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            PlannerResponse response = commandService.createPlanner(testUser.getId(), deviceId, request);

            // Assert
            assertNotNull(response);
            assertEquals("Test Planner", response.title());
            assertEquals("5F", response.category());
            assertEquals(1L, response.syncVersion());
            verify(contentValidator).validate(request.content(), request.category());
            verify(sseService).notifyPlannerUpdate(eq(testUser.getId()), eq(deviceId), any(UUID.class), eq("created"));
        }

        @Test
        @DisplayName("Should throw PlannerLimitExceededException when at max planners")
        void createPlanner_AtLimit_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn((long) maxPlannersPerUser);

            // Act & Assert
            PlannerLimitExceededException exception = assertThrows(
                    PlannerLimitExceededException.class,
                    () -> commandService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertTrue(exception.getMessage().contains(String.valueOf(maxPlannersPerUser)));
            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found")
        void createPlanner_UserNotFound_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            Long nonExistentUserId = 999L;
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(nonExistentUserId)).thenReturn(0L);
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
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should use default title when not provided")
        void createPlanner_NoTitle_UsesDefault() {
            // Arrange
            UpsertPlannerRequest request = withTitle(createValidRequest(), null);
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));

            ArgumentCaptor<Planner> plannerCaptor = ArgumentCaptor.forClass(Planner.class);
            when(plannerRepository.save(plannerCaptor.capture())).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
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
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            commandService.createPlanner(testUser.getId(), deviceId, request);

            // Assert - verify validation happens
            verify(contentValidator).validate(request.content(), request.category());
        }

        @Test
        @DisplayName("Should throw PlannerValidationException when content version is invalid")
        void createPlanner_InvalidContentVersion_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = withContentVersion(createValidRequest(), 5); // Old version
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
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
        void updatePlanner_Success_IncrementsSyncVersion() {
            // Arrange
            Planner planner = testPlannerBuilder().syncVersion(5L).build();

            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    "Updated Title", null, null, null, 5L, null);

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse response = commandService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false);

            // Assert
            assertEquals(6L, response.syncVersion());
            assertEquals("Updated Title", response.title());
            verify(sseService).notifyPlannerUpdate(testUser.getId(), deviceId, planner.getId(), "updated");
        }

        @Test
        @DisplayName("Should throw PlannerConflictException on version mismatch")
        void updatePlanner_VersionMismatch_ThrowsException() {
            // Arrange
            Planner planner = testPlannerBuilder().syncVersion(5L).build();

            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    "Updated Title", null, null, null, 3L, null); // Wrong version

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            // Act & Assert
            PlannerConflictException exception = assertThrows(
                    PlannerConflictException.class,
                    () -> commandService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false)
            );

            assertEquals(5L, exception.getActualVersion());
            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void updatePlanner_NotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    null, null, null, null, 1L, null);

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> commandService.updatePlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
        }

        @Test
        @DisplayName("Should validate content on update when provided")
        void updatePlanner_WithContent_ValidatesContent() {
            // Arrange
            Planner planner = createTestPlanner();
            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    null, null, null, "{\"updated\": \"content\"}", planner.getSyncVersion(), null);

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(contentValidator.validate(anyString(), anyString(), anyBoolean())).thenReturn(mock(JsonNode.class));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            commandService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false);

            // Assert - uses planner's category when request.category is null
            verify(contentValidator).validate(request.content(), planner.getCategory(), planner.getPublished());
        }

        @Test
        @DisplayName("Should only update provided fields")
        void updatePlanner_PartialUpdate_OnlyUpdatesProvidedFields() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setTitle("Original Title");
            planner.setStatus(PlannerStatus.DRAFT);

            UpdatePlannerRequest request = new UpdatePlannerRequest(
                    "New Title", null, null, null, planner.getSyncVersion(), null);
            // status not provided

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
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
        @DisplayName("Should soft delete planner successfully")
        void deletePlanner_Success_SoftDeletes() {
            // Arrange
            Planner planner = createTestPlanner();
            assertNull(planner.getDeletedAt());

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            commandService.deletePlanner(testUser.getId(), deviceId, planner.getId());

            // Assert
            assertNotNull(planner.getDeletedAt());
            assertTrue(planner.isDeleted());
            verify(plannerRepository).save(planner);
            verify(sseService).notifyPlannerUpdate(testUser.getId(), deviceId, planner.getId(), "deleted");
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when not found")
        void deletePlanner_NotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> commandService.deletePlanner(testUser.getId(), deviceId, plannerId)
            );

            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should auto-unpublish published planner before deletion")
        void deletePlanner_PublishedPlanner_UnpublishesFirst() {
            // Arrange
            Planner planner = testPlannerBuilder().published(true).build();
            assertTrue(planner.getPublished());

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            commandService.deletePlanner(testUser.getId(), deviceId, planner.getId());

            // Assert
            assertFalse(planner.getPublished()); // Auto-unpublished
            assertNotNull(planner.getDeletedAt()); // Then soft deleted
            verify(plannerRepository).save(planner);
        }

        @Test
        @DisplayName("Should not change unpublished planner on delete")
        void deletePlanner_UnpublishedPlanner_NoPublishChange() {
            // Arrange
            Planner planner = testPlannerBuilder().published(false).build();

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            commandService.deletePlanner(testUser.getId(), deviceId, planner.getId());

            // Assert
            assertFalse(planner.getPublished()); // Still unpublished
            assertNotNull(planner.getDeletedAt());
        }
    }

    @Nested
    @DisplayName("importPlanners Tests")
    class ImportPlannersTests {

        @Test
        @DisplayName("Should import planners successfully when within limit")
        void importPlanners_WithinLimit_Success() {
            // Arrange
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(50L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));

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
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            ImportPlannersResponse response = commandService.importPlanners(testUser.getId(), importRequest);

            // Assert
            assertEquals(3, response.imported());
            assertEquals(3, response.total());
            assertEquals(3, response.planners().size());
            verify(plannerRepository, times(3)).save(any(Planner.class));
            verify(contentValidator, times(3)).validate(anyString(), anyString());
        }

        @Test
        @DisplayName("Should reject import when would exceed limit")
        void importPlanners_ExceedsLimit_ThrowsException() {
            // Arrange
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn((long) (maxPlannersPerUser - 2));

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
        void importPlanners_UserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentUserId = 999L;
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(nonExistentUserId)).thenReturn(0L);
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
        void importPlanners_ExactlyToLimit_Success() {
            // Arrange
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn((long) (maxPlannersPerUser - 5));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));

            List<UpsertPlannerRequest> requests = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                requests.add(createValidRequest());
            }

            ImportPlannersRequest importRequest = new ImportPlannersRequest(requests);

            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            ImportPlannersResponse response = commandService.importPlanners(testUser.getId(), importRequest);

            // Assert
            assertEquals(5, response.imported());
            verify(plannerRepository, times(5)).save(any(Planner.class));
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
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn((long) (maxPlannersPerUser - 1));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act & Assert - should not throw
            assertDoesNotThrow(() -> commandService.createPlanner(testUser.getId(), deviceId, request));
            verify(plannerRepository).save(any(Planner.class));
        }

        @Test
        @DisplayName("Should fail creating planner when at max")
        void createPlanner_WhenAtMax_ThrowsLimitExceeded() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn((long) maxPlannersPerUser);

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
            // This is verified by the countByUserIdAndDeletedAtIsNull query being called
            // The service trusts the repository to only count non-deleted planners

            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            commandService.createPlanner(testUser.getId(), deviceId, request);

            // Verify that the correct method is called (the one that excludes deleted)
            verify(plannerRepository).countByUserIdAndDeletedAtIsNull(testUser.getId());
        }
    }

    @Nested
    @DisplayName("Ban Enforcement Tests")
    class BanEnforcementTests {

        @Test
        @DisplayName("Banned user cannot upsert planner")
        void upsertPlanner_bannedUser_throwsUserBannedException() {
            // Arrange
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);

            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));

            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    null, "5F", "Test Planner", null, "{}", 1, PlannerType.MIRROR_DUNGEON, null, null);

            UUID plannerId = UUID.randomUUID();

            // Act & Assert
            org.danteplanner.backend.user.exception.UserBannedException exception = assertThrows(
                    org.danteplanner.backend.user.exception.UserBannedException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
            assertEquals(testUser.getId(), exception.getUserId());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Non-banned user can upsert planner")
        void upsertPlanner_nonBannedUser_succeeds() {
            // Arrange
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId()))
                    .thenReturn(0L);
            when(plannerRepository.save(any(Planner.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpsertPlannerRequest request = new UpsertPlannerRequest(
                    null, "5F", "Test Planner", null, "{}", 1, PlannerType.MIRROR_DUNGEON, null, null);

            UUID plannerId = UUID.randomUUID();

            // Act
            org.danteplanner.backend.planner.dto.UpsertResult result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, false);

            // Assert
            assertNotNull(result);
            verify(plannerRepository).save(any());
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
        @DisplayName("Should throw PlannerNotFoundException when user's own planner is soft-deleted")
        void upsertPlanner_softDeletedByUser_throwsPlannerNotFoundException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = buildRequest();

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            when(plannerRepository.existsByIdAndUserId(plannerId, testUser.getId()))
                    .thenReturn(true);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> commandService.upsertPlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
            verify(plannerRepository, never()).save(any());
            verify(plannerRepository, never()).countByUserIdAndDeletedAtIsNull(any());
        }

        @Test
        @DisplayName("Should proceed to create when planner UUID is genuinely new")
        void upsertPlanner_genuinelyNewUUID_proceeds() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            UpsertPlannerRequest request = buildRequest();

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());
            when(plannerRepository.existsByIdAndUserId(plannerId, testUser.getId()))
                    .thenReturn(false);
            when(plannerRepository.existsByIdAndDeletedAtIsNull(plannerId))
                    .thenReturn(false);
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId()))
                    .thenReturn(0L);
            when(plannerRepository.save(any(Planner.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            UpsertResult result = commandService.upsertPlanner(
                    testUser.getId(), deviceId, plannerId, request, false);

            // Assert
            assertNotNull(result);
            assertTrue(result.isCreated());
            verify(plannerRepository).save(any());
        }
    }
}
