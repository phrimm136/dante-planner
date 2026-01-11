package org.danteplanner.backend.service;

import org.danteplanner.backend.dto.comment.CommentResponse;
import org.danteplanner.backend.dto.comment.CommentVoteResponse;
import org.danteplanner.backend.dto.comment.CreateCommentRequest;
import org.danteplanner.backend.dto.comment.UpdateCommentRequest;
import org.danteplanner.backend.entity.*;
import org.danteplanner.backend.exception.CommentForbiddenException;
import org.danteplanner.backend.exception.CommentNotFoundException;
import org.danteplanner.backend.exception.PlannerNotFoundException;
import org.danteplanner.backend.repository.PlannerCommentRepository;
import org.danteplanner.backend.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.repository.PlannerRepository;
import org.danteplanner.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommentService.
 * Tests CRUD operations and upvote toggle logic.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private PlannerCommentVoteRepository commentVoteRepository;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private CommentService commentService;

    private User testUser;
    private User otherUser;
    private Planner publishedPlanner;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(
                commentRepository,
                commentVoteRepository,
                plannerRepository,
                userRepository,
                notificationService
        );

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider("google")
                .providerId("test-123")
                .usernameKeyword("TEST")
                .usernameSuffix("tst01")
                .role(UserRole.NORMAL)
                .build();

        otherUser = User.builder()
                .id(2L)
                .email("other@example.com")
                .provider("google")
                .providerId("other-123")
                .usernameKeyword("OTHER")
                .usernameSuffix("oth01")
                .role(UserRole.NORMAL)
                .build();

        plannerId = UUID.randomUUID();
        publishedPlanner = Planner.builder()
                .id(plannerId)
                .user(testUser)
                .category("5F")
                .content("{}")
                .contentVersion(1)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(true)
                .build();
    }

    @Nested
    @DisplayName("createComment Tests")
    class CreateCommentTests {

        @Test
        @DisplayName("Creates top-level comment successfully")
        void createComment_topLevel_succeeds() {
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Test comment", null);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.save(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = i.getArgument(0);
                        c.setId(100L);
                        c.setCreatedAt(Instant.now());
                        return c;
                    });
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));

            // Act
            CommentResponse response = commentService.createComment(plannerId, testUser.getId(), request);

            // Assert
            assertEquals(100L, response.id());
            assertEquals(plannerId, response.plannerId());
            assertEquals(0, response.depth());
            assertNull(response.parentCommentId());
            assertEquals("Test comment", response.content());
            verify(commentRepository).save(any(PlannerComment.class));
        }

        @Test
        @DisplayName("Creates reply comment successfully")
        void createComment_reply_succeeds() {
            // Arrange
            PlannerComment parentComment = new PlannerComment(plannerId, otherUser.getId(), "Parent", null, 0);
            parentComment.setId(50L);
            parentComment.setCreatedAt(Instant.now());

            CreateCommentRequest request = new CreateCommentRequest("Reply comment", 50L);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findById(50L))
                    .thenReturn(Optional.of(parentComment));
            when(commentRepository.save(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = i.getArgument(0);
                        c.setId(101L);
                        c.setCreatedAt(Instant.now());
                        return c;
                    });
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));

            // Act
            CommentResponse response = commentService.createComment(plannerId, testUser.getId(), request);

            // Assert
            assertEquals(101L, response.id());
            assertEquals(1, response.depth());
            assertEquals(50L, response.parentCommentId());
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for non-existent planner")
        void createComment_plannerNotFound_throwsException() {
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Test", null);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PlannerNotFoundException.class,
                    () -> commentService.createComment(plannerId, testUser.getId(), request));
        }

        @Test
        @DisplayName("Throws CommentNotFoundException for non-existent parent")
        void createComment_parentNotFound_throwsException() {
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Reply", 999L);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findById(999L))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(CommentNotFoundException.class,
                    () -> commentService.createComment(plannerId, testUser.getId(), request));
        }

        @Test
        @DisplayName("Flattens replies at max depth")
        void createComment_atMaxDepth_flattensToParent() {
            // Arrange
            PlannerComment depth4Parent = new PlannerComment(plannerId, otherUser.getId(), "Deep", 40L, 5);
            depth4Parent.setId(50L);
            depth4Parent.setCreatedAt(Instant.now());
            depth4Parent.setParentCommentId(40L); // Parent's parent

            CreateCommentRequest request = new CreateCommentRequest("Very deep reply", 50L);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findById(50L))
                    .thenReturn(Optional.of(depth4Parent));
            when(commentRepository.save(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = i.getArgument(0);
                        c.setId(102L);
                        c.setCreatedAt(Instant.now());
                        return c;
                    });
            when(userRepository.findById(testUser.getId()))
                    .thenReturn(Optional.of(testUser));

            // Act
            CommentResponse response = commentService.createComment(plannerId, testUser.getId(), request);

            // Assert
            assertEquals(5, response.depth()); // Max depth
            assertEquals(40L, response.parentCommentId()); // Flattened to parent's parent
        }
    }

    @Nested
    @DisplayName("updateComment Tests")
    class UpdateCommentTests {

        @Test
        @DisplayName("Author can edit their comment")
        void updateComment_byAuthor_succeeds() {
            // Arrange
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);
            comment.setCreatedAt(Instant.now());

            UpdateCommentRequest request = new UpdateCommentRequest("Updated content");
            when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));
            when(commentRepository.save(any(PlannerComment.class))).thenAnswer(i -> i.getArgument(0));
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(commentVoteRepository.findByCommentIdAndUserId(100L, testUser.getId()))
                    .thenReturn(Optional.empty());

            // Act
            CommentResponse response = commentService.updateComment(100L, testUser.getId(), request);

            // Assert
            assertEquals("Updated content", response.content());
            assertNotNull(comment.getEditedAt());
        }

        @Test
        @DisplayName("Non-author cannot edit comment")
        void updateComment_byNonAuthor_throwsException() {
            // Arrange
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);

            UpdateCommentRequest request = new UpdateCommentRequest("Hacked content");
            when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> commentService.updateComment(100L, otherUser.getId(), request));
        }

        @Test
        @DisplayName("Cannot edit deleted comment")
        void updateComment_deletedComment_throwsException() {
            // Arrange
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);
            comment.softDelete();

            UpdateCommentRequest request = new UpdateCommentRequest("Updated");
            when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> commentService.updateComment(100L, testUser.getId(), request));
        }
    }

    @Nested
    @DisplayName("deleteComment Tests")
    class DeleteCommentTests {

        @Test
        @DisplayName("Author can delete their comment")
        void deleteComment_byAuthor_succeeds() {
            // Arrange
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "To delete", null, 0);
            comment.setId(100L);
            comment.setCreatedAt(Instant.now());

            when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));
            when(commentRepository.save(any(PlannerComment.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            commentService.deleteComment(100L, testUser.getId());

            // Assert
            assertTrue(comment.isDeleted());
            assertNull(comment.getContent()); // Content cleared on soft delete
            verify(commentRepository).save(comment);
        }

        @Test
        @DisplayName("Non-author cannot delete comment")
        void deleteComment_byNonAuthor_throwsException() {
            // Arrange
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Protected", null, 0);
            comment.setId(100L);

            when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> commentService.deleteComment(100L, otherUser.getId()));
            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deleting already deleted comment is idempotent")
        void deleteComment_alreadyDeleted_idempotent() {
            // Arrange
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), null, null, 0);
            comment.setId(100L);
            comment.softDelete();

            when(commentRepository.findById(100L)).thenReturn(Optional.of(comment));

            // Act
            commentService.deleteComment(100L, testUser.getId());

            // Assert - no exception, no save
            verify(commentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getComments Tests")
    class GetCommentsTests {

        @Test
        @DisplayName("Returns empty list for planner with no comments")
        void getComments_noComments_returnsEmptyList() {
            // Arrange
            when(plannerRepository.findById(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findByPlannerId(plannerId))
                    .thenReturn(Collections.emptyList());

            // Act
            List<CommentResponse> comments = commentService.getComments(plannerId, testUser.getId());

            // Assert
            assertTrue(comments.isEmpty());
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for non-existent planner")
        void getComments_plannerNotFound_throwsException() {
            // Arrange
            when(plannerRepository.findById(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PlannerNotFoundException.class,
                    () -> commentService.getComments(plannerId, testUser.getId()));
        }
    }
}
