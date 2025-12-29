package org.danteplanner.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.dto.planner.*;
import org.danteplanner.backend.entity.MDCategory;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.PlannerConflictException;
import org.danteplanner.backend.exception.PlannerLimitExceededException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
@ExtendWith(MockitoExtension.class)
class PlannerServiceTest {

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlannerSseService sseService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PlannerService plannerService;

    private User testUser;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .build();

        deviceId = UUID.randomUUID();
    }

    private CreatePlannerRequest createValidRequest() {
        CreatePlannerRequest request = new CreatePlannerRequest();
        request.setCategory(MDCategory.F5);
        request.setTitle("Test Planner");
        request.setStatus("draft");
        request.setContent("{\"data\": \"test\"}");
        return request;
    }

    private Planner createTestPlanner() {
        return Planner.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Test Planner")
                .category(MDCategory.F5)
                .status("draft")
                .content("{\"data\": \"test\"}")
                .syncVersion(1L)
                .version(1)
                .createdAt(Instant.now())
                .lastModifiedAt(Instant.now())
                .savedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("createPlanner Tests")
    class CreatePlannerTests {

        @Test
        @DisplayName("Should create planner successfully when within limit")
        void createPlanner_WithinLimit_Success() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(50L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);
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
            assertEquals(MDCategory.F5, response.getCategory());
            assertEquals(1L, response.getSyncVersion());
            verify(sseService).notifyPlannerUpdate(eq(testUser.getId()), eq(deviceId), any(UUID.class), eq("created"));
        }

        @Test
        @DisplayName("Should throw PlannerLimitExceededException when at 100 planners")
        void createPlanner_AtLimit_ThrowsException() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(100L);

            // Act & Assert
            PlannerLimitExceededException exception = assertThrows(
                    PlannerLimitExceededException.class,
                    () -> plannerService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertTrue(exception.getMessage().contains("100"));
            verify(plannerRepository, never()).save(any());
            verify(sseService, never()).notifyPlannerUpdate(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw PlannerValidationException when content exceeds 50KB")
        void createPlanner_ContentTooLarge_ThrowsException() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            String largeContent = "{\"data\": \"" + "x".repeat(52000) + "\"}";
            request.setContent(largeContent);
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> plannerService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertEquals("CONTENT_TOO_LARGE", exception.getErrorCode());
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerValidationException when note exceeds 1KB")
        void createPlanner_NoteTooLarge_ThrowsException() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            String largeNote = "x".repeat(1100);
            String contentWithLargeNote = "{\"sectionNotes\": {\"section1\": {\"content\": \"" + largeNote + "\"}}}";
            request.setContent(contentWithLargeNote);
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> plannerService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertEquals("NOTE_TOO_LARGE", exception.getErrorCode());
            assertTrue(exception.getMessage().contains("section1"));
            verify(plannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should use default title when not provided")
        void createPlanner_NoTitle_UsesDefault() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            request.setTitle(null);
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);

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
            PlannerResponse response = plannerService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request);

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
                    () -> plannerService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request)
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
                    () -> plannerService.updatePlanner(testUser.getId(), deviceId, plannerId, request)
            );
        }

        @Test
        @DisplayName("Should validate content size on update")
        void updatePlanner_ContentTooLarge_ThrowsException() {
            // Arrange
            Planner planner = createTestPlanner();
            UpdatePlannerRequest request = new UpdatePlannerRequest();
            request.setSyncVersion(planner.getSyncVersion());
            request.setContent("{\"data\": \"" + "x".repeat(52000) + "\"}");

            when(plannerRepository.findByIdAndUserIdAndDeletedAtIsNull(planner.getId(), testUser.getId()))
                    .thenReturn(Optional.of(planner));

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> plannerService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request)
            );

            assertEquals("CONTENT_TOO_LARGE", exception.getErrorCode());
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
            PlannerResponse response = plannerService.updatePlanner(testUser.getId(), deviceId, planner.getId(), request);

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
    }

    @Nested
    @DisplayName("importPlanners Tests")
    class ImportPlannersTests {

        @Test
        @DisplayName("Should import planners successfully when within limit")
        void importPlanners_WithinLimit_Success() {
            // Arrange
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(50L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);

            List<CreatePlannerRequest> requests = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                CreatePlannerRequest req = createValidRequest();
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
        }

        @Test
        @DisplayName("Should reject import when would exceed 100 limit")
        void importPlanners_ExceedsLimit_ThrowsException() {
            // Arrange
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(98L);

            List<CreatePlannerRequest> requests = new ArrayList<>();
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
        @DisplayName("Should validate content size for each planner in import")
        void importPlanners_ContentTooLarge_ThrowsException() {
            // Arrange
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);

            List<CreatePlannerRequest> requests = new ArrayList<>();
            CreatePlannerRequest validReq = createValidRequest();
            requests.add(validReq);

            CreatePlannerRequest largeReq = createValidRequest();
            largeReq.setContent("{\"data\": \"" + "x".repeat(52000) + "\"}");
            requests.add(largeReq);

            ImportPlannersRequest importRequest = new ImportPlannersRequest();
            importRequest.setPlanners(requests);

            // Save first one successfully
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> plannerService.importPlanners(testUser.getId(), importRequest)
            );

            assertEquals("CONTENT_TOO_LARGE", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should allow import up to exactly 100 planners")
        void importPlanners_ExactlyToLimit_Success() {
            // Arrange
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(95L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);

            List<CreatePlannerRequest> requests = new ArrayList<>();
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
    @DisplayName("Content Validation Tests")
    class ContentValidationTests {

        @Test
        @DisplayName("Should accept content at exactly 50KB")
        void contentAt50KB_Succeeds() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            // Create content just under 50KB (account for JSON structure overhead)
            String content = "{\"d\":\"" + "x".repeat(51190) + "\"}";
            assertTrue(content.getBytes().length <= 50 * 1024);
            request.setContent(content);

            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act & Assert - should not throw
            assertDoesNotThrow(() -> plannerService.createPlanner(testUser.getId(), deviceId, request));
        }

        @Test
        @DisplayName("Should reject content just over 50KB")
        void contentOver50KB_Fails() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            // Create content just over 50KB
            String content = "{\"d\":\"" + "x".repeat(51300) + "\"}";
            assertTrue(content.getBytes().length > 50 * 1024);
            request.setContent(content);

            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);

            // Act & Assert
            PlannerValidationException exception = assertThrows(
                    PlannerValidationException.class,
                    () -> plannerService.createPlanner(testUser.getId(), deviceId, request)
            );

            assertEquals("CONTENT_TOO_LARGE", exception.getErrorCode());
        }

        @Test
        @DisplayName("Should accept note at exactly 1KB")
        void noteAt1KB_Succeeds() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            // Create a note just under 1KB
            String noteContent = "x".repeat(900);
            String content = "{\"sectionNotes\":{\"s1\":{\"content\":\"" + noteContent + "\"}}}";
            request.setContent(content);

            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act & Assert - should not throw
            assertDoesNotThrow(() -> plannerService.createPlanner(testUser.getId(), deviceId, request));
        }

        @Test
        @DisplayName("Should handle content with no sectionNotes gracefully")
        void contentWithoutSectionNotes_Succeeds() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            request.setContent("{\"data\": \"test\", \"otherField\": 123}");

            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act & Assert - should not throw
            assertDoesNotThrow(() -> plannerService.createPlanner(testUser.getId(), deviceId, request));
        }

        @Test
        @DisplayName("Should handle empty sectionNotes gracefully")
        void contentWithEmptySectionNotes_Succeeds() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            request.setContent("{\"sectionNotes\": {}}");

            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.save(any(Planner.class))).thenAnswer(invocation -> {
                Planner planner = invocation.getArgument(0);
                planner.setCreatedAt(Instant.now());
                planner.setLastModifiedAt(Instant.now());
                return planner;
            });

            // Act & Assert - should not throw
            assertDoesNotThrow(() -> plannerService.createPlanner(testUser.getId(), deviceId, request));
        }
    }

    @Nested
    @DisplayName("Planner Limit Edge Cases")
    class PlannerLimitEdgeCaseTests {

        @Test
        @DisplayName("Should allow creating planner when at 99")
        void createAt99_Succeeds() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(99L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);
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
        @DisplayName("Should fail creating planner when at 100")
        void createAt100_Fails() {
            // Arrange
            CreatePlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(100L);

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

            CreatePlannerRequest request = createValidRequest();
            when(plannerRepository.countByUserIdAndDeletedAtIsNull(testUser.getId())).thenReturn(0L);
            when(userRepository.getReferenceById(testUser.getId())).thenReturn(testUser);
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
}
