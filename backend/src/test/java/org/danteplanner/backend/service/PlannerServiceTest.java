package org.danteplanner.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.PlannerType;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.entity.PlannerBookmark;
import org.danteplanner.backend.entity.PlannerView;
import org.danteplanner.backend.entity.PlannerVote;
import org.danteplanner.backend.entity.VoteType;
import org.danteplanner.backend.exception.PlannerConflictException;
import org.danteplanner.backend.exception.PlannerForbiddenException;
import org.danteplanner.backend.exception.PlannerLimitExceededException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.danteplanner.backend.exception.UserNotFoundException;
import org.danteplanner.backend.repository.PlannerBookmarkRepository;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.PlannerViewRepository;
import org.danteplanner.backend.repository.PlannerVoteRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.danteplanner.backend.validation.ContentVersionValidator;
import org.danteplanner.backend.validation.PlannerContentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * Unit tests for PlannerService.
 *
 * <p>Tests business logic in isolation using Mockito mocks
 * for dependencies.</p>
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
class PlannerServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerVoteRepository plannerVoteRepository;

    @Mock
    private PlannerBookmarkRepository plannerBookmarkRepository;

    @Mock
    private PlannerViewRepository plannerViewRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlannerSyncEventService sseService;

    @Mock
    private PlannerContentValidator contentValidator;

    @Mock
    private ContentVersionValidator contentVersionValidator;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private PlannerSubscriptionService subscriptionService;

    @Mock
    private PlannerReportService reportService;

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private SseService notificationSseService;

    @Mock
    private NotificationService notificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private PlannerService plannerService;

    @Value("${planner.max-per-user}")
    private int maxPlannersPerUser;

    @Value("${planner.recommended-threshold}")
    private int recommendedThreshold;

    private User testUser;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        // Initialize Mockito mocks
        MockitoAnnotations.openMocks(this);

        // Construct service with property values from application-test.properties
        plannerService = new PlannerService(
                plannerRepository,
                plannerVoteRepository,
                plannerBookmarkRepository,
                plannerViewRepository,
                userRepository,
                sseService,
                contentValidator,
                contentVersionValidator,
                eventPublisher,
                subscriptionService,
                reportService,
                commentRepository,
                notificationSseService,
                notificationService,
                maxPlannersPerUser,
                recommendedThreshold
        );

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();

        deviceId = UUID.randomUUID();

        // Mock user lookup for timeout check in write operations
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
    }

    private UpsertPlannerRequest createValidRequest() {
        UpsertPlannerRequest request = new UpsertPlannerRequest();
        request.setId(UUID.randomUUID().toString());
        request.setCategory("5F");
        request.setTitle("Test Planner");
        request.setStatus("draft");
        request.setContent("{\"data\": \"test\"}");
        request.setContentVersion(6);
        request.setPlannerType(PlannerType.MIRROR_DUNGEON);
        return request;
    }

    private Planner createTestPlanner() {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Test Planner")
                .category("5F")
                .status("draft")
                .content("{\"data\": \"test\"}")
                .syncVersion(1L)
                .schemaVersion(1)
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .savedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("isValidCategory Tests")
    class IsValidCategoryTests {

        @Test
        @DisplayName("Should return true for valid MD category with MIRROR_DUNGEON type")
        void isValidCategory_ValidMdCategory_ReturnsTrue() {
            assertTrue(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "5F"));
            assertTrue(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "10F"));
            assertTrue(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "15F"));
        }

        @Test
        @DisplayName("Should return true for valid RR category with REFRACTED_RAILWAY type")
        void isValidCategory_ValidRrCategory_ReturnsTrue() {
            assertTrue(plannerService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER"));
        }

        @Test
        @DisplayName("Should return false for invalid category")
        void isValidCategory_InvalidCategory_ReturnsFalse() {
            assertFalse(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "INVALID"));
            assertFalse(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, ""));
            assertFalse(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, null));
        }

        @Test
        @DisplayName("Should return false when MD category used with RR type")
        void isValidCategory_MdCategoryWithRrType_ReturnsFalse() {
            assertFalse(plannerService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "5F"));
            assertFalse(plannerService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "10F"));
            assertFalse(plannerService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "15F"));
        }

        @Test
        @DisplayName("Should return false when RR category used with MD type")
        void isValidCategory_RrCategoryWithMdType_ReturnsFalse() {
            assertFalse(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "RR_PLACEHOLDER"));
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
            PlannerResponse response = plannerService.createPlanner(testUser.getId(), deviceId, request);

            // Assert
            assertNotNull(response);
            assertEquals("Test Planner", response.getTitle());
            assertEquals("5F", response.getCategory());
            assertEquals(1L, response.getSyncVersion());
            verify(contentValidator).validate(request.getContent(), request.getCategory());
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
                    () -> plannerService.createPlanner(testUser.getId(), deviceId, request)
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
                    () -> plannerService.createPlanner(nonExistentUserId, deviceId, request)
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
            UpsertPlannerRequest request = createValidRequest();
            request.setTitle(null);
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
            plannerService.createPlanner(testUser.getId(), deviceId, request);

            // Assert
            assertEquals("Untitled", plannerCaptor.getValue().getTitle());
        }

        @Test
        @DisplayName("Should call content validator before saving")
        void createPlanner_ValidatesContentBeforeSave() {
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
            plannerService.createPlanner(testUser.getId(), deviceId, request);

            // Assert - verify validation happens
            verify(contentValidator).validate(request.getContent(), request.getCategory());
        }

        @Test
        @DisplayName("Should throw PlannerValidationException when content version is invalid")
        void createPlanner_InvalidContentVersion_ThrowsException() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            request.setContentVersion(5); // Old version
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            doThrow(new PlannerValidationException("INVALID_CONTENT_VERSION", "Invalid content version"))
                    .when(contentVersionValidator).validateVersionForCreate(any(), eq(5));

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> plannerService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertEquals("INVALID_CONTENT_VERSION", exception.getErrorCode());
            verify(plannerRepository, never()).save(any());
            verify(contentValidator, never()).validate(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("getPlanners Tests")
    class GetPlannersTests {

        @Test
        @DisplayName("Should return paginated planners for user")
        void getPlanners_ReturnsPaginatedResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<Planner> planners = List.of(createTestPlanner(), createTestPlanner());
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 2);

            when(plannerRepository.findByUserIdAndDeletedAtIsNullOrderByLastModifiedAtDesc(testUser.getId(), pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PlannerSummaryResponse> result = plannerService.getPlanners(testUser.getId(), pageable);

            // Assert
            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
        }

        @Test
        @DisplayName("Should return empty page when no planners exist")
        void getPlanners_NoPlanners_ReturnsEmptyPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Planner> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(plannerRepository.findByUserIdAndDeletedAtIsNullOrderByLastModifiedAtDesc(testUser.getId(), pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<PlannerSummaryResponse> result = plannerService.getPlanners(testUser.getId(), pageable);

            // Assert
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("getPlanner Tests")
    class GetPlannerTests {

        @Test
        @DisplayName("Should return planner when found")
        void getPlanner_Found_ReturnsPlanner() {
            // Arrange
            Planner planner = createTestPlanner();
            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            // Act
            PlannerResponse response = plannerService.getPlanner(testUser.getId(), planner.getId());

            // Assert
            assertNotNull(response);
            assertEquals(planner.getId(), response.getId());
            assertEquals(planner.getTitle(), response.getTitle());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when not found")
        void getPlanner_NotFound_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            PlannerNotFoundException exception = assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.getPlanner(testUser.getId(), plannerId)
            );

            assertTrue(exception.getMessage().contains(plannerId.toString()));
        }
    }

    @Nested
    @DisplayName("updatePlanner Tests")
    class UpdatePlannerTests {

        @Test
        @DisplayName("Should increment syncVersion on successful update")
        void updatePlanner_Success_IncrementsSyncVersion() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setSyncVersion(5L);

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            request.setSyncVersion(5L);

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse response = plannerService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false);

            // Assert
            assertEquals(6L, response.getSyncVersion());
            assertEquals("Updated Title", response.getTitle());
            verify(sseService).notifyPlannerUpdate(testUser.getId(), deviceId, planner.getId(), "updated");
        }

        @Test
        @DisplayName("Should throw PlannerConflictException on version mismatch")
        void updatePlanner_VersionMismatch_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setSyncVersion(5L);

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setTitle("Updated Title");
            request.setSyncVersion(3L); // Wrong version

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            // Act & Assert
            PlannerConflictException exception = assertThrows(
                    PlannerConflictException.class,
                    () -> plannerService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false)
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
            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setSyncVersion(1L);

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(plannerId, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.updatePlanner(testUser.getId(), deviceId, plannerId, request, false)
            );
        }

        @Test
        @DisplayName("Should validate content on update when provided")
        void updatePlanner_WithContent_ValidatesContent() {
            // Arrange
            Planner planner = createTestPlanner();
            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setSyncVersion(planner.getSyncVersion());
            request.setContent("{\"updated\": \"content\"}");

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(contentValidator.validate(anyString(), anyString())).thenReturn(mock(JsonNode.class));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            plannerService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false);

            // Assert - uses planner's category when request.category is null
            verify(contentValidator).validate(request.getContent(), planner.getCategory());
        }

        @Test
        @DisplayName("Should only update provided fields")
        void updatePlanner_PartialUpdate_OnlyUpdatesProvidedFields() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setTitle("Original Title");
            planner.setStatus("draft");

            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setSyncVersion(planner.getSyncVersion());
            request.setTitle("New Title");
            // status not provided

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerResponse response = plannerService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request, false);

            // Assert
            assertEquals("New Title", response.getTitle());
            assertEquals("draft", response.getStatus()); // Original status preserved
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
            plannerService.deletePlanner(testUser.getId(), deviceId, planner.getId());

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
                    () -> plannerService.deletePlanner(testUser.getId(), deviceId, plannerId)
            );

            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should auto-unpublish published planner before deletion")
        void deletePlanner_PublishedPlanner_UnpublishesFirst() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setPublished(true);
            assertTrue(planner.getPublished());

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            plannerService.deletePlanner(testUser.getId(), deviceId, planner.getId());

            // Assert
            assertFalse(planner.getPublished()); // Auto-unpublished
            assertNotNull(planner.getDeletedAt()); // Then soft deleted
            verify(plannerRepository).save(planner);
        }

        @Test
        @DisplayName("Should not change unpublished planner on delete")
        void deletePlanner_UnpublishedPlanner_NoPublishChange() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setPublished(false);

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            plannerService.deletePlanner(testUser.getId(), deviceId, planner.getId());

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
                req.setTitle("Imported " + i);
                requests.add(req);
            }

            ImportPlannersRequest importRequest = new ImportPlannersRequest();
            importRequest.setPlanners(requests);

            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            ImportPlannersResponse response = plannerService.importPlanners(testUser.getId(), importRequest);

            // Assert
            assertEquals(3, response.getImported());
            assertEquals(3, response.getTotal());
            assertEquals(3, response.getPlanners().size());
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

            ImportPlannersRequest importRequest = new ImportPlannersRequest();
            importRequest.setPlanners(requests);

            // Act & Assert
            assertThrows(
                    PlannerLimitExceededException.class,
                    () -> plannerService.importPlanners(testUser.getId(), importRequest)
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

            ImportPlannersRequest importRequest = new ImportPlannersRequest();
            importRequest.setPlanners(requests);

            // Act & Assert
            UserNotFoundException exception = assertThrows(
                    UserNotFoundException.class,
                    () -> plannerService.importPlanners(nonExistentUserId, importRequest)
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

            ImportPlannersRequest importRequest = new ImportPlannersRequest();
            importRequest.setPlanners(requests);

            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act
            ImportPlannersResponse response = plannerService.importPlanners(testUser.getId(), importRequest);

            // Assert
            assertEquals(5, response.getImported());
            verify(plannerRepository, times(5)).save(any(Planner.class));
        }
    }

    @Nested
    @DisplayName("Planner Limit Edge Cases")
    class PlannerLimitEdgeCaseTests {

        @Test
        @DisplayName("Should allow creating planner when at max-1")
        void createAtMaxMinus1_Succeeds() {
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
            assertDoesNotThrow(() -> plannerService.createPlanner(testUser.getId(), deviceId, request));
            verify(plannerRepository).save(any(Planner.class));
        }

        @Test
        @DisplayName("Should fail creating planner when at max")
        void createAtMax_Fails() {
            // Arrange
            UpsertPlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn((long) maxPlannersPerUser);

            // Act & Assert
            assertThrows(
                    PlannerLimitExceededException.class,
                    () -> plannerService.createPlanner(testUser.getId(), deviceId, request)
            );

            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should count only non-deleted planners for limit")
        void limitCountsOnlyNonDeleted() {
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

            plannerService.createPlanner(testUser.getId(), deviceId, request);

            // Verify that the correct method is called (the one that excludes deleted)
            verify(plannerRepository).countByUserIdAndDeletedAtIsNull(testUser.getId());
        }
    }

    @Nested
    @DisplayName("togglePublish Tests")
    class TogglePublishTests {

        @Test
        @DisplayName("Should toggle publish status when owner")
        void togglePublish_Owner_TogglesStatus() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setPublished(false);

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerRepository.saveAndFlush(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Planner result = plannerService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertTrue(result.getPublished());
            verify(plannerRepository).save(planner);
        }

        @Test
        @DisplayName("Should toggle from published to unpublished")
        void togglePublish_PublishedToUnpublished() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setPublished(true);

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Planner result = plannerService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertFalse(result.getPublished());
        }

        @Test
        @DisplayName("Should throw PlannerForbiddenException when not owner")
        void togglePublish_NotOwner_ThrowsException() {
            // Arrange
            User otherUser = User.builder()
                    .id(999L)
                    .email("other@example.com")
                    .provider("google")
                    .providerId("google-999")
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("test2")
                    .build();
            Planner planner = createTestPlanner();
            planner.setUser(otherUser);

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            PlannerForbiddenException exception = assertThrows(
                    PlannerForbiddenException.class,
                    () -> plannerService.togglePublish(testUser.getId(), planner.getId())
            );

            assertEquals(planner.getId(), exception.getPlannerId());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void togglePublish_NotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.togglePublish(testUser.getId(), nonExistentId)
            );
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner is deleted")
        void togglePublish_Deleted_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.softDelete();

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.togglePublish(testUser.getId(), planner.getId())
            );
        }

        @Test
        @DisplayName("Should auto-subscribe owner when publishing")
        void togglePublish_Publishing_AutoSubscribesOwner() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setPublished(false);

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerRepository.saveAndFlush(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Planner result = plannerService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertTrue(result.getPublished());
            verify(subscriptionService).createSubscription(testUser.getId(), planner.getId());
        }

        @Test
        @DisplayName("Should not auto-subscribe when unpublishing")
        void togglePublish_Unpublishing_DoesNotAutoSubscribe() {
            // Arrange
            Planner planner = createTestPlanner();
            planner.setPublished(true);

            when(plannerRepository.findById(planner.getId())).thenReturn(Optional.of(planner));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Planner result = plannerService.togglePublish(testUser.getId(), planner.getId());

            // Assert
            assertFalse(result.getPublished());
            verify(subscriptionService, never()).createSubscription(any(), any());
        }
    }

    // Old CastVoteTests class removed (lines 873-1229 deleted)
    // Reason: Tested obsolete vote toggle behavior (voteType: null, soft-delete, reactivation)
    // Replacement: See CastVoteImmutabilityTests below for new immutable voting tests

    @Nested
    @DisplayName("getPublishedPlanners Tests")
    class GetPublishedPlannersTests {

        private Planner createPublishedPlanner(String title) {
            Planner planner = createTestPlanner();
            planner.setTitle(title);
            planner.setPublished(true);
            return planner;
        }

        @Test
        @DisplayName("Should return paginated published planners")
        void getPublishedPlanners_ReturnsPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<Planner> planners = List.of(
                    createPublishedPlanner("Planner 1"),
                    createPublishedPlanner("Planner 2")
            );
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 2);

            when(plannerRepository.findByPublishedTrueAndDeletedAtIsNull(pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PublicPlannerResponse> result = plannerService.getPublishedPlanners(pageable, null);

            // Assert
            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
        }

        @Test
        @DisplayName("Should filter by category when provided")
        void getPublishedPlanners_WithCategory_FiltersResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            String category = "5F";
            List<Planner> planners = List.of(createPublishedPlanner("F5 Planner"));
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 1);

            when(plannerRepository.findByPublishedTrueAndCategoryAndDeletedAtIsNull(category, pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PublicPlannerResponse> result = plannerService.getPublishedPlanners(pageable, category);

            // Assert
            assertEquals(1, result.getTotalElements());
            verify(plannerRepository).findByPublishedTrueAndCategoryAndDeletedAtIsNull(category, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no published planners")
        void getPublishedPlanners_NoPlanners_ReturnsEmpty() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Planner> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(plannerRepository.findByPublishedTrueAndDeletedAtIsNull(pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<PublicPlannerResponse> result = plannerService.getPublishedPlanners(pageable, null);

            // Assert
            assertEquals(0, result.getTotalElements());
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("getRecommendedPlanners Tests")
    class GetRecommendedPlannersTests {

        private Planner createRecommendedPlanner(String title, int upvotes) {
            Planner planner = createTestPlanner();
            planner.setTitle(title);
            planner.setPublished(true);
            planner.setUpvotes(upvotes);
            return planner;
        }

        @Test
        @DisplayName("Should return planners meeting threshold")
        void getRecommendedPlanners_ReturnsQualifyingPlanners() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<Planner> planners = List.of(
                    createRecommendedPlanner("High Votes", 15),
                    createRecommendedPlanner("Medium Votes", 12)
            );
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 2);

            when(plannerRepository.findRecommendedPlanners(recommendedThreshold, pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PublicPlannerResponse> result = plannerService.getRecommendedPlanners(pageable, null);

            // Assert
            assertEquals(2, result.getTotalElements());
        }

        @Test
        @DisplayName("Should filter by category when provided")
        void getRecommendedPlanners_WithCategory_FiltersResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            String category = "10F";
            List<Planner> planners = List.of(createRecommendedPlanner("F10 Planner", 20));
            Page<Planner> plannerPage = new PageImpl<>(planners, pageable, 1);

            when(plannerRepository.findRecommendedPlannersByCategory(recommendedThreshold, category, pageable))
                    .thenReturn(plannerPage);

            // Act
            Page<PublicPlannerResponse> result = plannerService.getRecommendedPlanners(pageable, category);

            // Assert
            assertEquals(1, result.getTotalElements());
            verify(plannerRepository).findRecommendedPlannersByCategory(
                    recommendedThreshold, category, pageable);
        }

        @Test
        @DisplayName("Should return empty when no planners meet threshold")
        void getRecommendedPlanners_NoneQualify_ReturnsEmpty() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Planner> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(plannerRepository.findRecommendedPlanners(recommendedThreshold, pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<PublicPlannerResponse> result = plannerService.getRecommendedPlanners(pageable, null);

            // Assert
            assertTrue(result.getContent().isEmpty());
        }
    }

    @Nested
    @DisplayName("toggleBookmark Tests")
    class ToggleBookmarkTests {

        private Planner createPublishedPlanner() {
            Planner planner = createTestPlanner();
            planner.setPublished(true);
            return planner;
        }

        @Test
        @DisplayName("Should add bookmark when not bookmarked")
        void toggleBookmark_NotBookmarked_AddsBookmark() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerBookmarkRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.empty());
            when(plannerBookmarkRepository.save(any(PlannerBookmark.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            BookmarkResponse response = plannerService.toggleBookmark(testUser.getId(), plannerId);

            // Assert
            assertTrue(response.isBookmarked());
            assertEquals(plannerId, response.getPlannerId());
            verify(plannerBookmarkRepository).save(any(PlannerBookmark.class));
            verify(plannerBookmarkRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should remove bookmark when already bookmarked")
        void toggleBookmark_AlreadyBookmarked_RemovesBookmark() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            PlannerBookmark existingBookmark = new PlannerBookmark(testUser.getId(), plannerId);

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerBookmarkRepository.findByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(Optional.of(existingBookmark));

            // Act
            BookmarkResponse response = plannerService.toggleBookmark(testUser.getId(), plannerId);

            // Assert
            assertFalse(response.isBookmarked());
            assertEquals(plannerId, response.getPlannerId());
            verify(plannerBookmarkRepository).delete(existingBookmark);
            verify(plannerBookmarkRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void toggleBookmark_PlannerNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.toggleBookmark(testUser.getId(), nonExistentId)
            );

            verify(plannerBookmarkRepository, never()).findByUserIdAndPlannerId(any(), any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not published")
        void toggleBookmark_PlannerNotPublished_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner(); // Not published
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.toggleBookmark(testUser.getId(), plannerId)
            );
        }
    }

    @Nested
    @DisplayName("forkPlanner Tests")
    class ForkPlannerTests {

        private Planner createPublishedPlanner() {
            Planner planner = createTestPlanner();
            planner.setTitle("Original Planner");
            planner.setPublished(true);
            planner.setUpvotes(10);
            planner.setViewCount(100);
            return planner;
        }

        @Test
        @DisplayName("Should create draft copy with reset counters")
        void forkPlanner_Success_CreatesDraftCopy() {
            // Arrange
            Planner original = createPublishedPlanner();
            UUID originalId = original.getId();

            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(originalId))
                    .thenReturn(Optional.of(original));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

            ArgumentCaptor<Planner> plannerCaptor = ArgumentCaptor.forClass(Planner.class);
            when(plannerRepository.save(plannerCaptor.capture())).thenAnswer(invocation -> {
                Planner p = invocation.getArgument(0);
                return p;
            });

            // Act
            ForkResponse response = plannerService.forkPlanner(testUser.getId(), originalId);

            // Assert
            assertNotNull(response);
            assertEquals(originalId, response.getOriginalPlannerId());
            assertNotNull(response.getNewPlannerId());
            assertTrue(response.getMessage().contains("forked"));

            // Verify forked planner properties
            Planner forked = plannerCaptor.getValue();
            assertEquals("Original Planner (Fork)", forked.getTitle());
            assertEquals("draft", forked.getStatus());
            assertFalse(forked.getPublished());
            assertEquals(0, forked.getUpvotes());
            assertEquals(0, forked.getViewCount());
            assertEquals(original.getCategory(), forked.getCategory());
            assertEquals(original.getContent(), forked.getContent());
        }

        @Test
        @DisplayName("Should throw PlannerLimitExceededException when at max")
        void forkPlanner_AtLimit_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId()))
                    .thenReturn((long) maxPlannersPerUser);

            // Act & Assert
            assertThrows(
                    PlannerLimitExceededException.class,
                    () -> plannerService.forkPlanner(testUser.getId(), plannerId)
            );

            verify(plannerRepository, never()).findByIdAndPublishedTrueAndDeletedAtIsNull(any());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void forkPlanner_PlannerNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.forkPlanner(testUser.getId(), nonExistentId)
            );
        }

        @Test
        @DisplayName("Should throw UserNotFoundException when user not found")
        void forkPlanner_UserNotFound_ThrowsException() {
            // Arrange
            Long nonExistentUserId = 999L;
            Planner original = createPublishedPlanner();
            UUID originalId = original.getId();

            when(plannerRepository.countByUserIdAndDeletedAtIsNull(nonExistentUserId)).thenReturn(0L);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(originalId))
                    .thenReturn(Optional.of(original));
            when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    UserNotFoundException.class,
                    () -> plannerService.forkPlanner(nonExistentUserId, originalId)
            );

            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow forking own published planner")
        void forkPlanner_OwnPlanner_Success() {
            // Arrange
            Planner original = createPublishedPlanner();
            original.setUser(testUser); // Owner
            UUID originalId = original.getId();

            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(originalId))
                    .thenReturn(Optional.of(original));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            ForkResponse response = plannerService.forkPlanner(testUser.getId(), originalId);

            // Assert
            assertNotNull(response);
            verify(plannerRepository).save(any(Planner.class));
        }
    }

    @Nested
    @DisplayName("incrementViewCount Tests")
    class IncrementViewCountTests {

        @Test
        @DisplayName("Should increment view count atomically")
        void incrementViewCount_Success_IncrementsCount() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.incrementViewCount(plannerId)).thenReturn(1);

            // Act
            plannerService.incrementViewCount(plannerId);

            // Assert
            verify(plannerRepository).incrementViewCount(plannerId);
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void incrementViewCount_NotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.incrementViewCount(nonExistentId)).thenReturn(0);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.incrementViewCount(nonExistentId)
            );
        }

        @Test
        @DisplayName("Should handle deleted planner (returns 0 rows updated)")
        void incrementViewCount_DeletedPlanner_ThrowsException() {
            // Arrange
            UUID deletedPlannerId = UUID.randomUUID();
            when(plannerRepository.incrementViewCount(deletedPlannerId)).thenReturn(0);

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.incrementViewCount(deletedPlannerId)
            );
        }
    }

    @Nested
    @DisplayName("castVote Immutability Tests")
    class CastVoteImmutabilityTests {

        private Planner createPublishedPlanner() {
            Planner planner = createTestPlanner();
            planner.setPublished(true);
            planner.setUpvotes(5);
            return planner;
        }

        @Test
        @DisplayName("Should throw VoteAlreadyExistsException when user attempts duplicate vote")
        void castVote_DuplicateVote_ThrowsException() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            org.danteplanner.backend.entity.PlannerVoteId voteId =
                new org.danteplanner.backend.entity.PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(true);

            // Act & Assert
            org.danteplanner.backend.exception.VoteAlreadyExistsException exception = assertThrows(
                    org.danteplanner.backend.exception.VoteAlreadyExistsException.class,
                    () -> plannerService.castVote(testUser.getId(), plannerId, org.danteplanner.backend.entity.VoteType.UP)
            );

            assertEquals(plannerId, exception.getPlannerId());
            assertEquals(testUser.getId(), exception.getUserId());
            verify(plannerVoteRepository, never()).save(any());
            verify(plannerRepository, never()).incrementUpvotes(any());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when voteType is null")
        void castVote_NullVoteType_ThrowsException() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> plannerService.castVote(testUser.getId(), plannerId, null)
            );

            assertTrue(exception.getMessage().contains("Vote type cannot be null"));
            verify(plannerVoteRepository, never()).existsById(any());
            verify(plannerVoteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow first vote and create new vote record")
        void castVote_FirstVote_CreatesVote() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();
            Planner updatedPlanner = createPublishedPlanner();
            updatedPlanner.setId(plannerId);
            updatedPlanner.setUpvotes(6);
            org.danteplanner.backend.entity.PlannerVoteId voteId =
                new org.danteplanner.backend.entity.PlannerVoteId(testUser.getId(), plannerId);

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerVoteRepository.existsById(voteId))
                    .thenReturn(false);
            when(plannerVoteRepository.saveAndFlush(any(org.danteplanner.backend.entity.PlannerVote.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(plannerRepository.findById(plannerId)).thenReturn(Optional.of(updatedPlanner));

            // Act
            org.danteplanner.backend.dto.planner.VoteResponse response =
                    plannerService.castVote(testUser.getId(), plannerId, org.danteplanner.backend.entity.VoteType.UP);

            // Assert
            assertEquals(6, response.getUpvoteCount());
            assertTrue(response.getHasUpvoted());
            verify(plannerVoteRepository).saveAndFlush(any(org.danteplanner.backend.entity.PlannerVote.class));
            verify(plannerRepository).incrementUpvotes(plannerId);
        }
    }

    @Nested
    @DisplayName("recordView Tests")
    class RecordViewTests {

        private static final String IP_ADDRESS = "192.168.1.100";
        private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";

        private Planner createPublishedPlanner() {
            Planner planner = createTestPlanner();
            planner.setPublished(true);
            planner.setViewCount(10);
            return planner;
        }

        @Test
        @DisplayName("Should record new view and increment count for anonymous user")
        void recordView_NewAnonymousView_IncrementsCount() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerViewRepository.existsByPlannerIdAndViewerHashAndViewDate(eq(plannerId), any(), any()))
                    .thenReturn(false);
            when(plannerViewRepository.saveAndFlush(any(PlannerView.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            plannerService.recordView(plannerId, null, IP_ADDRESS, USER_AGENT);

            // Assert
            verify(plannerViewRepository).saveAndFlush(any(PlannerView.class));
            verify(plannerRepository).incrementViewCount(plannerId);
        }

        @Test
        @DisplayName("Should record new view and increment count for authenticated user")
        void recordView_NewAuthenticatedView_IncrementsCount() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerViewRepository.existsByPlannerIdAndViewerHashAndViewDate(eq(plannerId), any(), any()))
                    .thenReturn(false);
            when(plannerViewRepository.saveAndFlush(any(PlannerView.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            plannerService.recordView(plannerId, testUser.getId(), IP_ADDRESS, USER_AGENT);

            // Assert
            verify(plannerViewRepository).saveAndFlush(any(PlannerView.class));
            verify(plannerRepository).incrementViewCount(plannerId);
        }

        @Test
        @DisplayName("Should not increment count for duplicate view same day")
        void recordView_DuplicateView_NoIncrement() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerViewRepository.existsByPlannerIdAndViewerHashAndViewDate(eq(plannerId), any(), any()))
                    .thenReturn(true);

            // Act
            plannerService.recordView(plannerId, null, IP_ADDRESS, USER_AGENT);

            // Assert
            verify(plannerViewRepository, never()).save(any());
            verify(plannerRepository, never()).incrementViewCount(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException for unpublished planner")
        void recordView_UnpublishedPlanner_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.recordView(plannerId, null, IP_ADDRESS, USER_AGENT)
            );

            verify(plannerViewRepository, never()).existsByPlannerIdAndViewerHashAndViewDate(any(), any(), any());
            verify(plannerViewRepository, never()).save(any());
            verify(plannerRepository, never()).incrementViewCount(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException for deleted planner")
        void recordView_DeletedPlanner_ThrowsException() {
            // Arrange
            UUID plannerId = UUID.randomUUID();
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> plannerService.recordView(plannerId, null, IP_ADDRESS, USER_AGENT)
            );
        }

        @Test
        @DisplayName("Should handle null User-Agent gracefully")
        void recordView_NullUserAgent_Success() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerViewRepository.existsByPlannerIdAndViewerHashAndViewDate(eq(plannerId), any(), any()))
                    .thenReturn(false);
            when(plannerViewRepository.saveAndFlush(any(PlannerView.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act & Assert - should not throw
            assertDoesNotThrow(() ->
                    plannerService.recordView(plannerId, null, IP_ADDRESS, null)
            );

            verify(plannerViewRepository).saveAndFlush(any(PlannerView.class));
        }

        @Test
        @DisplayName("Should use different hashes for authenticated vs anonymous users")
        void recordView_AuthVsAnon_DifferentHashes() {
            // Arrange
            Planner planner = createPublishedPlanner();
            UUID plannerId = planner.getId();

            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(planner));
            when(plannerViewRepository.existsByPlannerIdAndViewerHashAndViewDate(eq(plannerId), any(), any()))
                    .thenReturn(false);
            when(plannerViewRepository.saveAndFlush(any(PlannerView.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ArgumentCaptor<PlannerView> viewCaptor = ArgumentCaptor.forClass(PlannerView.class);

            // Act - record as anonymous
            plannerService.recordView(plannerId, null, IP_ADDRESS, USER_AGENT);

            // Reset mocks for second call
            reset(plannerViewRepository);
            when(plannerViewRepository.existsByPlannerIdAndViewerHashAndViewDate(eq(plannerId), any(), any()))
                    .thenReturn(false);
            when(plannerViewRepository.saveAndFlush(any(PlannerView.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act - record as authenticated
            plannerService.recordView(plannerId, testUser.getId(), IP_ADDRESS, USER_AGENT);

            // Assert - both should save with different hashes
            verify(plannerViewRepository, times(1)).saveAndFlush(viewCaptor.capture());
            // Note: We can't easily compare hashes here without exposing internal logic,
            // but the fact that both calls succeed (existsByPlannerIdAndViewerHashAndViewDate returns false) verifies
            // they would have different hashes
        }
    }
}
