package org.danteplanner.backend.service;

import org.danteplanner.backend.moderation.service.PlannerReportService;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.danteplanner.backend.moderation.validation.ReporterStandingValidator;
import org.danteplanner.backend.moderation.entity.PlannerReport;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.moderation.exception.ReportAlreadyExistsException;
import org.danteplanner.backend.moderation.repository.PlannerReportRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.service.PlannerModerationTargetAdapter;

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
    private PlannerRepository plannerRepository;

    private PlannerReportService reportService;

    private User testUser;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        FakeUsers users = new FakeUsers();
        reportService = new PlannerReportService(reportRepository,
                new ReporterStandingValidator(users),
                new PlannerModerationTargetAdapter(null,
                        new PlannerAccessGuard(users, plannerRepository)),
                new ReportUniquenessValidator());

        testUser = users.add(TestDataFactory.unsavedUser(1L));

        plannerId = UUID.randomUUID();
    }

    @Nested
    class CreateReportTests {

        @Test
        void createReport_WhenNotReported_CreatesReport() {
            // Arrange
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);
            when(reportRepository.insert(any(PlannerReport.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            reportService.createReport(testUser.getId(), plannerId);

            // Assert
            ArgumentCaptor<PlannerReport> captor = ArgumentCaptor.forClass(PlannerReport.class);
            verify(reportRepository).insert(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(testUser.getId());
            assertThat(captor.getValue().getPlannerId()).isEqualTo(plannerId);
        }

        @Test
        void createReport_WhenAlreadyReported_ThrowsException() {
            // Arrange
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(true);

            // Act & Assert
            assertRejected(() -> reportService.createReport(testUser.getId(), plannerId))
                    .withCode("REPORT_ALREADY_EXISTS")
                    .isInstanceOfSatisfying(ReportAlreadyExistsException.class, exception -> {
                        assertThat(exception.getPlannerId()).isEqualTo(plannerId);
                        assertThat(exception.getUserId()).isEqualTo(testUser.getId());
                    });
            verify(reportRepository, never()).insert(any());
        }

        @Test
        void createReport_WhenPlannerMissingOrUnpublished_ThrowsException() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(plannerRepository.existsPublishedById(nonExistentId)).thenReturn(false);

            // Act & Assert
            assertRejected(() -> reportService.createReport(testUser.getId(), nonExistentId))
                    .withCode("PLANNER_NOT_FOUND");

            verify(reportRepository, never()).existsByUserIdAndPlannerId(any(), any());
            verify(reportRepository, never()).insert(any());
        }
    }

    @Nested
    class HasReportedTests {

        @Test
        void hasReported_WhenReported_ReturnsTrue() {
            // Arrange
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(true);

            // Act
            boolean result = reportService.hasReported(testUser.getId(), plannerId);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void hasReported_WhenNotReported_ReturnsFalse() {
            // Arrange
            when(reportRepository.existsByUserIdAndPlannerId(testUser.getId(), plannerId))
                    .thenReturn(false);

            // Act
            boolean result = reportService.hasReported(testUser.getId(), plannerId);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
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
