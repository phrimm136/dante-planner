package org.danteplanner.backend.service;

import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.moderation.exception.ReportAlreadyExistsException;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.user.repository.UserRepository;

/**
 * Unit tests for PlannerReportService.
 *
 * <p>Tests report creation and duplicate prevention logic in isolation using Mockito mocks.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlannerReportServiceTest {

    @Mock
    private PlannerReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlannerRepository plannerRepository;

    private PlannerReportService reportService;

    private User testUser;
    private Planner publishedPlanner;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        reportService = new PlannerReportService(reportRepository,
                new PlannerAccessGuard(userRepository, plannerRepository));

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();

        plannerId = UUID.randomUUID();

        publishedPlanner = TestDataFactory.planner(testUser)
                .id(plannerId)
                .title("Published Planner")
                .category("5F")
                .status(PlannerStatus.DRAFT)
                .content("{\"data\": \"test\"}")
                .contentVersion(6)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(true)
                .build();
        // The access guard resolves the principal on every guarded path; an unstubbed
        // repository would surface as UserNotFoundException instead of the behavior under test.
        lenient().when(userRepository.findById(anyLong())).thenReturn(Optional.of(testUser));

    }

    @Nested
    @DisplayName("createReport Tests")
    class CreateReportTests {

        @Test
        @DisplayName("Should create report when not previously reported")
        void createReport_WhenNotReported_CreatesReport() {
            // Arrange
            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);
            when(reportRepository.save(any(PlannerReport.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            PlannerReport result = reportService.createReport(testUser.getId(), plannerId);

            // Assert
            assertNotNull(result);
            assertEquals(testUser.getId(), result.getUserId());
            assertEquals(plannerId, result.getPlannerId());

            ArgumentCaptor<PlannerReport> captor = ArgumentCaptor.forClass(PlannerReport.class);
            verify(reportRepository).save(captor.capture());
            assertEquals(testUser.getId(), captor.getValue().getUserId());
            assertEquals(plannerId, captor.getValue().getPlannerId());
        }

        @Test
        @DisplayName("Should throw ReportAlreadyExistsException when already reported")
        void createReport_WhenAlreadyReported_ThrowsException() {
            // Arrange
            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(true);

            // Act & Assert
            ReportAlreadyExistsException exception = assertThrows(
                    ReportAlreadyExistsException.class,
                    () -> reportService.createReport(testUser.getId(), plannerId)
            );

            assertEquals(plannerId, exception.getPlannerId());
            assertEquals(testUser.getId(), exception.getUserId());
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not found")
        void createReport_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findPublishedAggregate(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> reportService.createReport(testUser.getId(), nonExistentId)
            );

            verify(reportRepository, never()).existsByUserIdAndPlannerId(any(), any());
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw PlannerNotFoundException when planner not published")
        void createReport_WhenPlannerNotPublished_ThrowsException() {
            // Arrange
            when(plannerRepository.findPublishedAggregate(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> reportService.createReport(testUser.getId(), plannerId)
            );
        }

        @Test
        @DisplayName("Should validate planner before checking existing report")
        void createReport_WhenPlannerMissing_ValidatesPlannerFirst() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.findPublishedAggregate(nonExistentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(
                    PlannerNotFoundException.class,
                    () -> reportService.createReport(testUser.getId(), nonExistentId)
            );

            // Verify order: planner lookup first, then never reaches report check
            verify(plannerRepository).findPublishedAggregate(nonExistentId);
            verify(reportRepository, never()).existsByUserIdAndPlannerId(any(), any());
        }
    }

    @Nested
    @DisplayName("hasReported Tests")
    class HasReportedTests {

        @Test
        @DisplayName("Should return true when user has reported")
        void hasReported_WhenReported_ReturnsTrue() {
            // Arrange
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(true);

            // Act
            boolean result = reportService.hasReported(testUser.getId(), plannerId);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when user has not reported")
        void hasReported_WhenNotReported_ReturnsFalse() {
            // Arrange
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);

            // Act
            boolean result = reportService.hasReported(testUser.getId(), plannerId);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Should check correct user and planner IDs")
        void hasReported_WhenCalled_ChecksCorrectIds() {
            // Arrange
            Long userId = 42L;
            UUID specificPlannerId = UUID.randomUUID();
            when(reportRepository.existsByUserIdAndPlannerId(userId, specificPlannerId))
                    .thenReturn(true);

            // Act
            reportService.hasReported(userId, specificPlannerId);

            // Assert
            verify(reportRepository).existsByUserIdAndPlannerId(userId, specificPlannerId);
        }
    }
}
