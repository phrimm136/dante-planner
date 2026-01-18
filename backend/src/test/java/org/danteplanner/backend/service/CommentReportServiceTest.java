package org.danteplanner.backend.service;

import org.danteplanner.backend.dto.comment.CommentReportRequest;
import org.danteplanner.backend.dto.comment.CommentReportResponse;
import org.danteplanner.backend.entity.PlannerComment;
import org.danteplanner.backend.entity.PlannerCommentReport;
import org.danteplanner.backend.exception.CommentForbiddenException;
import org.danteplanner.backend.exception.CommentNotFoundException;
import org.danteplanner.backend.exception.CommentReportAlreadyExistsException;
import org.danteplanner.backend.repository.PlannerCommentReportRepository;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        service = new CommentReportService(reportRepository, commentRepository);
    }

    @Nested
    @DisplayName("createReport Tests")
    class CreateReportTests {

        private PlannerComment comment;

        @BeforeEach
        void setUp() {
            comment = new PlannerComment(PLANNER_ID, 50L, "content", null, 0);
            comment.setId(COMMENT_INTERNAL_ID);
            comment.setPublicId(COMMENT_PUBLIC_ID);
        }

        @Test
        @DisplayName("Should create report when comment exists and not already reported")
        void createReport_WhenNotReported_CreatesReport() {
            // Arrange
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.of(comment));
            when(reportRepository.existsByReporterIdAndCommentId(REPORTER_ID, COMMENT_INTERNAL_ID))
                    .thenReturn(false);
            when(reportRepository.save(any(PlannerCommentReport.class)))
                    .thenAnswer(inv -> {
                        PlannerCommentReport r = inv.getArgument(0);
                        r.setCreatedAt(java.time.Instant.now());
                        return r;
                    });

            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act
            CommentReportResponse response = service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request);

            // Assert
            assertNotNull(response.createdAt());
            verify(reportRepository).save(any(PlannerCommentReport.class));
        }

        @Test
        @DisplayName("Should throw CommentNotFoundException when comment not found")
        void createReport_WhenCommentNotFound_ThrowsException() {
            // Arrange
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.empty());
            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act & Assert
            assertThrows(CommentNotFoundException.class,
                    () -> service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request));

            verify(reportRepository, never()).existsByReporterIdAndCommentId(any(), any());
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw CommentReportAlreadyExistsException when already reported")
        void createReport_WhenAlreadyReported_ThrowsException() {
            // Arrange
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.of(comment));
            when(reportRepository.existsByReporterIdAndCommentId(REPORTER_ID, COMMENT_INTERNAL_ID))
                    .thenReturn(true);
            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act & Assert
            CommentReportAlreadyExistsException exception = assertThrows(
                    CommentReportAlreadyExistsException.class,
                    () -> service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request)
            );

            assertEquals(COMMENT_INTERNAL_ID, exception.getCommentId());
            assertEquals(REPORTER_ID, exception.getUserId());
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw CommentForbiddenException when comment is deleted")
        void createReport_WhenCommentDeleted_ThrowsException() {
            // Arrange
            comment.softDelete();
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.of(comment));
            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request));

            verify(reportRepository, never()).existsByReporterIdAndCommentId(any(), any());
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should validate comment before checking existing report")
        void createReport_ValidatesCommentFirst() {
            // Arrange
            when(commentRepository.findByPublicId(COMMENT_PUBLIC_ID)).thenReturn(Optional.empty());
            CommentReportRequest request = new CommentReportRequest(REASON);

            // Act & Assert
            assertThrows(CommentNotFoundException.class,
                    () -> service.createReport(COMMENT_PUBLIC_ID, REPORTER_ID, request));

            // Verify order: comment lookup first, then never reaches report check
            verify(commentRepository).findByPublicId(COMMENT_PUBLIC_ID);
            verify(reportRepository, never()).existsByReporterIdAndCommentId(any(), any());
        }
    }

    @Nested
    @DisplayName("hasReported Tests")
    class HasReportedTests {

        @Test
        @DisplayName("Should return true when user has reported")
        void hasReported_WhenReported_ReturnsTrue() {
            // Arrange
            when(reportRepository.existsByReporterIdAndCommentId(REPORTER_ID, COMMENT_INTERNAL_ID))
                    .thenReturn(true);

            // Act
            boolean result = service.hasReported(REPORTER_ID, COMMENT_INTERNAL_ID);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when user has not reported")
        void hasReported_WhenNotReported_ReturnsFalse() {
            // Arrange
            when(reportRepository.existsByReporterIdAndCommentId(REPORTER_ID, COMMENT_INTERNAL_ID))
                    .thenReturn(false);

            // Act
            boolean result = service.hasReported(REPORTER_ID, COMMENT_INTERNAL_ID);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Should check correct user and comment IDs")
        void hasReported_ChecksCorrectIds() {
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
