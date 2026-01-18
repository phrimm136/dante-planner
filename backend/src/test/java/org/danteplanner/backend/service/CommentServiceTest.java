package org.danteplanner.backend.service;

import org.danteplanner.backend.dto.comment.CommentTreeNode;
import org.danteplanner.backend.dto.comment.CreateCommentResponse;
import org.danteplanner.backend.dto.comment.UpdateCommentResponse;
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
import static org.mockito.ArgumentMatchers.argThat;
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

    @Mock
    private PlannerCommentSseService plannerCommentSseService;

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
                notificationService,
                plannerCommentSseService
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
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            // Act
            UUID deviceId = UUID.randomUUID();
            CreateCommentResponse response = commentService.createComment(plannerId, testUser.getId(), deviceId, request);

            // Assert
            assertNotNull(response.id());
            assertNotNull(response.createdAt());
            verify(commentRepository).save(any(PlannerComment.class));
        }

        @Test
        @DisplayName("Creates reply comment successfully")
        void createComment_reply_succeeds() {
            // Arrange
            UUID parentPublicId = UUID.randomUUID();
            PlannerComment parentComment = new PlannerComment(plannerId, otherUser.getId(), "Parent", null, 0);
            parentComment.setId(50L);
            parentComment.setPublicId(parentPublicId);
            parentComment.setCreatedAt(Instant.now());

            CreateCommentRequest request = new CreateCommentRequest("Reply comment", parentPublicId);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findByPublicId(parentPublicId))
                    .thenReturn(Optional.of(parentComment));
            when(commentRepository.findById(50L))
                    .thenReturn(Optional.of(parentComment));
            when(commentRepository.save(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = i.getArgument(0);
                        c.setId(101L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            // Act
            UUID deviceId = UUID.randomUUID();
            CreateCommentResponse response = commentService.createComment(plannerId, testUser.getId(), deviceId, request);

            // Assert
            assertNotNull(response.id());
            assertNotNull(response.createdAt());
            verify(commentRepository).save(argThat(c -> c.getDepth() == 1 && c.getParentCommentId().equals(50L)));
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for non-existent planner")
        void createComment_plannerNotFound_throwsException() {
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Test", null);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            UUID deviceId = UUID.randomUUID();
            assertThrows(PlannerNotFoundException.class,
                    () -> commentService.createComment(plannerId, testUser.getId(), deviceId, request));
        }

        @Test
        @DisplayName("Throws CommentNotFoundException for non-existent parent")
        void createComment_parentNotFound_throwsException() {
            // Arrange
            UUID nonExistentParentId = UUID.randomUUID();
            CreateCommentRequest request = new CreateCommentRequest("Reply", nonExistentParentId);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findByPublicId(nonExistentParentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            UUID deviceId = UUID.randomUUID();
            assertThrows(CommentNotFoundException.class,
                    () -> commentService.createComment(plannerId, testUser.getId(), deviceId, request));
        }

        @Test
        @DisplayName("Allows deeply nested replies without flattening (MAX_DEPTH is unlimited)")
        void createComment_deepNesting_allowedWithoutFlattening() {
            // Arrange
            // MAX_DEPTH is Integer.MAX_VALUE, so no flattening occurs
            UUID parentPublicId = UUID.randomUUID();
            PlannerComment depth5Parent = new PlannerComment(plannerId, otherUser.getId(), "Deep", 40L, 5);
            depth5Parent.setId(50L);
            depth5Parent.setPublicId(parentPublicId);
            depth5Parent.setCreatedAt(Instant.now());
            depth5Parent.setParentCommentId(40L);

            CreateCommentRequest request = new CreateCommentRequest("Very deep reply", parentPublicId);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findByPublicId(parentPublicId))
                    .thenReturn(Optional.of(depth5Parent));
            when(commentRepository.findById(50L))
                    .thenReturn(Optional.of(depth5Parent));
            when(commentRepository.save(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = i.getArgument(0);
                        c.setId(102L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            // Act
            UUID deviceId = UUID.randomUUID();
            CreateCommentResponse response = commentService.createComment(plannerId, testUser.getId(), deviceId, request);

            // Assert
            assertNotNull(response.id());
            assertNotNull(response.createdAt());
            // With unlimited MAX_DEPTH, reply is direct child of parent at depth 6
            verify(commentRepository).save(argThat(c -> c.getDepth() == 6 && c.getParentCommentId().equals(50L)));
        }
    }

    @Nested
    @DisplayName("updateComment Tests")
    class UpdateCommentTests {

        @Test
        @DisplayName("Author can edit their comment")
        void updateComment_byAuthor_succeeds() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);
            comment.setCreatedAt(Instant.now());

            UpdateCommentRequest request = new UpdateCommentRequest("Updated content");
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));
            when(commentRepository.save(any(PlannerComment.class))).thenAnswer(i -> {
                PlannerComment c = i.getArgument(0);
                c.setEditedAt(Instant.now());
                return c;
            });

            // Act
            UpdateCommentResponse response = commentService.updateComment(commentPublicId, testUser.getId(), request);

            // Assert
            assertNotNull(response.editedAt());
        }

        @Test
        @DisplayName("Non-author cannot edit comment")
        void updateComment_byNonAuthor_throwsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);

            UpdateCommentRequest request = new UpdateCommentRequest("Hacked content");
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> commentService.updateComment(commentPublicId, otherUser.getId(), request));
        }

        @Test
        @DisplayName("Cannot edit deleted comment")
        void updateComment_deletedComment_throwsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);
            comment.softDelete();

            UpdateCommentRequest request = new UpdateCommentRequest("Updated");
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> commentService.updateComment(commentPublicId, testUser.getId(), request));
        }
    }

    @Nested
    @DisplayName("deleteComment Tests")
    class DeleteCommentTests {

        @Test
        @DisplayName("Author can delete their comment")
        void deleteComment_byAuthor_succeeds() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "To delete", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);
            comment.setCreatedAt(Instant.now());

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));
            when(commentRepository.save(any(PlannerComment.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            commentService.deleteComment(commentPublicId, testUser.getId());

            // Assert
            assertTrue(comment.isDeleted());
            assertEquals("", comment.getContent()); // Content cleared on soft delete
            verify(commentRepository).save(comment);
        }

        @Test
        @DisplayName("Non-author cannot delete comment")
        void deleteComment_byNonAuthor_throwsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Protected", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> commentService.deleteComment(commentPublicId, otherUser.getId()));
            verify(commentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deleting already deleted comment is idempotent")
        void deleteComment_alreadyDeleted_idempotent() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), null, null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);
            comment.softDelete();

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act
            commentService.deleteComment(commentPublicId, testUser.getId());

            // Assert - no exception, no save
            verify(commentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getCommentTree Tests")
    class GetCommentTreeTests {

        @Test
        @DisplayName("Returns empty list for planner with no comments")
        void getCommentTree_noComments_returnsEmptyList() {
            // Arrange
            when(plannerRepository.findById(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findByPlannerId(plannerId))
                    .thenReturn(Collections.emptyList());

            // Act
            List<CommentTreeNode> comments = commentService.getCommentTree(plannerId, testUser.getId());

            // Assert
            assertTrue(comments.isEmpty());
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for non-existent planner")
        void getCommentTree_plannerNotFound_throwsException() {
            // Arrange
            when(plannerRepository.findById(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PlannerNotFoundException.class,
                    () -> commentService.getCommentTree(plannerId, testUser.getId()));
        }
    }
}
