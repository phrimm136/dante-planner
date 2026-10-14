package org.danteplanner.backend.service;

import org.danteplanner.backend.moderation.service.CommentReportService;
import org.danteplanner.backend.moderation.validation.ReportUniquenessValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.mockito.ArgumentCaptor;
import org.danteplanner.backend.moderation.api.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.api.dto.CommentReportResponse;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.moderation.entity.PlannerCommentReport;
import org.danteplanner.backend.moderation.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.moderation.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.service.CommentModerationTargetAdapter;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
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
                        null, new CommentStateValidator()),
                new ReportUniquenessValidator());
    }

    @Nested
    class CreateReportTests {

        private PlannerComment comment;

        @BeforeEach
        void setUp() {
            comment = new PlannerComment(PLANNER_ID, 50L, "content", null, 0);
            comment.setId(COMMENT_INTERNAL_ID);
            comment.setPublicId(COMMENT_PUBLIC_ID);
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
        void createReport_WhenCommentDeleted_ThrowsException() {
            // Arrange
            comment.softDelete();
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.of(comment));
            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act & Assert
            assertRejected(() -> service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Cannot report a deleted comment"));

            verify(reportRepository, never()).existsByReporterIdAndCommentId(any(), any());
            verify(reportRepository, never()).insert(any());
        }
    }

    @Nested
    class HasReportedTests {

        @Test
        void hasReported_WhenReported_ReturnsTrue() {
            // Arrange
            when(reportRepository.existsByReporterIdAndCommentId(REPORTER_ID, COMMENT_INTERNAL_ID))
                    .thenReturn(true);

            // Act
            boolean result = service.hasReported(REPORTER_ID, COMMENT_INTERNAL_ID);

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        void hasReported_WhenNotReported_ReturnsFalse() {
            // Arrange
            when(reportRepository.existsByReporterIdAndCommentId(REPORTER_ID, COMMENT_INTERNAL_ID))
                    .thenReturn(false);

            // Act
            boolean result = service.hasReported(REPORTER_ID, COMMENT_INTERNAL_ID);

            // Assert
            assertThat(result).isFalse();
        }

        @Test
        void hasReported_WhenCalled_ChecksCorrectIds() {
            // Arrange
            Long userId = 42L;
            Long specificCommentId = 999L;
            when(reportRepository.existsByReporterIdAndCommentId(userId, specificCommentId))
                    .thenReturn(true);

            // Act
            service.hasReported(userId, specificCommentId);

            // Assert
            verify(reportRepository).existsByReporterIdAndCommentId(userId, specificCommentId);
        }
    }
}
