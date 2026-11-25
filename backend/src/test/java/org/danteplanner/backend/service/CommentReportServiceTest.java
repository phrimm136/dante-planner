package org.danteplanner.backend.service;

import org.danteplanner.backend.support.CommentRows;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.moderation.service.CommentReportService;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.mockito.ArgumentCaptor;
import org.danteplanner.backend.moderation.api.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.api.dto.CommentReportResponse;
import org.danteplanner.backend.moderation.entity.PlannerCommentReport;
import org.danteplanner.backend.moderation.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.moderation.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.service.CommentModerationTargetAdapter;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.support.IntegrityViolations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * Unit tests for CommentReportService.
 * Tests report creation and duplicate prevention logic.
 */
@ExtendWith(MockitoExtension.class)
class CommentReportServiceTest {

    @Mock
    private PlannerCommentReportRepository reportRepository;

    @Mock
    private PlannerCommentRepository commentRepository;

    private CommentReportService service;

    private static final Long COMMENT_INTERNAL_ID = 1L;
    private static final UUID COMMENT_PUBLIC_ID = UUID.randomUUID();
    private static final Long REPORTER_ID = 100L;
    private static final String REASON = "SPAM";
    private static final UUID PLANNER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommentReportService(reportRepository,
                new CommentModerationTargetAdapter(new CommentQueryService(commentRepository, null, null, null, null),
                        null),
                new ReportUniquenessValidator());
    }

    @Nested
    class CreateReportTests {

        private CommentRow comment;

        @BeforeEach
        void setUp() {
            comment = CommentRows.live(COMMENT_INTERNAL_ID, COMMENT_PUBLIC_ID, PLANNER_ID, 50L, "content", null, 0);
        }

        @Test
        void createReport_WhenNotReported_CreatesReport() {
            // Arrange
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.of(comment));
            when(reportRepository.existsByReporterIdAndCommentId(REPORTER_ID, COMMENT_INTERNAL_ID))
                    .thenReturn(false);
            when(reportRepository.insert(any(PlannerCommentReport.class)))
                    .thenAnswer(inv -> {
                        PlannerCommentReport r = inv.getArgument(0);
                        r.setCreatedAt(java.time.Instant.now());
                        return r;
                    });

            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act
            CommentReportResponse response = service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request);

            // Assert
            assertThat(response.createdAt()).isNotNull();

            ArgumentCaptor<PlannerCommentReport> captor =
                    ArgumentCaptor.forClass(PlannerCommentReport.class);
            verify(reportRepository).insert(captor.capture());
            assertThat(captor.getValue().getReporterId()).isEqualTo(REPORTER_ID);
            assertThat(captor.getValue().getReason()).isEqualTo(REASON);
        }

        @Test
        void createReport_WhenCommentNotFound_ThrowsException() {
            // Arrange
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.empty());
            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act & Assert
            assertRejected(() -> service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request))
                    .withCode("COMMENT_NOT_FOUND");

            verify(reportRepository, never()).existsByReporterIdAndCommentId(any(), any());
            verify(reportRepository, never()).insert(any());
        }

        @Test
        void createReport_WhenAlreadyReported_ThrowsException() {
            // Arrange
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.of(comment));
            when(reportRepository.existsByReporterIdAndCommentId(REPORTER_ID, COMMENT_INTERNAL_ID))
                    .thenReturn(true);
            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act & Assert
            assertRejected(() -> service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request))
                    .withCode("COMMENT_REPORT_ALREADY_EXISTS")
                    .isInstanceOfSatisfying(CommentReportAlreadyExistsException.class, exception -> {
                        assertThat(exception.getCommentId()).isEqualTo(COMMENT_INTERNAL_ID);
                        assertThat(exception.getUserId()).isEqualTo(REPORTER_ID);
                    });
            verify(reportRepository, never()).insert(any());
        }

        @Test
        void createReport_WhenAConcurrentReportWins_SurfacesTheConstraint() {
            DataIntegrityViolationException duplicate =
                    IntegrityViolations.duplicateEntry("planner_comment_reports.uk_comment_report_reporter_comment");
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.of(comment));
            when(reportRepository.insert(any(PlannerCommentReport.class))).thenThrow(duplicate);

            assertThatThrownBy(() -> service.createReport(
                    COMMENT_PUBLIC_ID, REPORTER_ID, new CommentReportRequest(REASON))).isSameAs(duplicate);
        }

        @Test
        void createReport_WhenCommentDeleted_ThrowsException() {
            // Arrange
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID))
                    .thenReturn(Optional.of(CommentRows.deleted(comment, Instant.now())));
            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act & Assert
            assertRejected(() -> service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Cannot report a deleted comment"));

            verify(reportRepository, never()).existsByReporterIdAndCommentId(any(), any());
            verify(reportRepository, never()).insert(any());
        }
    }
}
