package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentEngagementService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.planner.validation.VoteUniquenessValidator;
import org.danteplanner.backend.moderation.service.CommentReportService;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;


import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.comment.dto.UpdateCommentResponse;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.exception.CommentForbiddenException;
import org.danteplanner.backend.comment.exception.CommentNotFoundException;
import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;
import org.danteplanner.backend.user.exception.UserBannedException;
import org.danteplanner.backend.user.exception.UserTimedOutException;

/**
 * Unit tests for the comment service layer.
 * Tests CRUD operations and upvote toggle logic.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceLayerTest {

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private PlannerCommentVoteRepository commentVoteRepository;

    @Mock
    private PlannerRepository plannerRepository;

    // The comment counter lives on the stats aggregate, reachable only through this repository;
    // its value becomes observable only in a tier that commits.
    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private UserService userService;

    @Mock
    private DomainEventRecorder domainEventRecorder;


    // A comment write records one domain event and nothing else; who hears about it is decided by
    // the arm that dispatches the row, so no create test here can assert delivery.

    @Mock
    private CommentReportService commentReportService;


    private CommentQueryService queryService;
    private CommentCommandService commandService;
    private CommentEngagementService engagementService;

    private User testUser;
    private User otherUser;
    private Planner publishedPlanner;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        queryService = new CommentQueryService(
                commentRepository, commentVoteRepository, userService,
                new PlannerAccessGuard(userService, plannerRepository),
                new CommentAccessValidator());
        commandService = new CommentCommandService(
                commentRepository, queryService, domainEventRecorder,
                new PlannerAccessGuard(userService, plannerRepository),
                new PlannerStatsService(plannerStatsRepository),
                new CommentAccessValidator(), new CommentAuthorshipValidator(),
                new CommentStateValidator());
        engagementService = new CommentEngagementService(
                commentRepository, commentVoteRepository, queryService,
                new PlannerAccessGuard(userService, plannerRepository),
                commentReportService, new CommentAuthorshipValidator(),
                new CommentStateValidator(), new VoteUniquenessValidator());

        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("test-123")
                .usernameEpithet("TEST")
                .usernameSuffix("tst01")
                .role(UserRole.NORMAL)
                .build();

        otherUser = User.builder()
                .id(2L)
                .email("other@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("other-123")
                .usernameEpithet("OTHER")
                .usernameSuffix("oth01")
                .role(UserRole.NORMAL)
                .build();

        plannerId = UUID.randomUUID();
        publishedPlanner = TestDataFactory.planner(testUser)
                .id(plannerId)
                .category("5F")
                .content("{}")
                .contentVersion(1)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(true)
                .build();
        // The access guard resolves the principal on every guarded path; an unstubbed
        // repository would surface as UserNotFoundException instead of the behavior under test.
        lenient().when(userService.findById(anyLong())).thenReturn(testUser);

    }

    @Nested
    @DisplayName("createComment Tests")
    class CreateCommentTests {

        @Test
        @DisplayName("Creates top-level comment successfully")
        void createComment_WhenTopLevel_Succeeds() {
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Test comment", null);
            AtomicReference<PlannerComment> persisted = new AtomicReference<>();
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = i.getArgument(0);
                        c.setId(100L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        persisted.set(c);
                        return c;
                    });

            // Act
            UUID deviceId = UUID.randomUUID();
            CreateCommentResponse response = commandService.createComment(plannerId, testUser.getId(), deviceId, request);

            // Assert
            PlannerComment stored = persisted.get();
            assertEquals(plannerId, stored.getPlannerId());
            assertEquals(testUser.getId(), stored.getUserId());
            assertEquals("Test comment", stored.getContent());
            assertEquals(0, stored.getDepth());
            assertNull(stored.getParentCommentId());
            assertEquals(stored.getPublicId(), response.id());
            assertEquals(stored.getCreatedAt(), response.createdAt());
        }

        @Test
        @DisplayName("Creates reply comment successfully")
        void createComment_WhenReply_Succeeds() {
            // Arrange
            UUID parentPublicId = UUID.randomUUID();
            PlannerComment parentComment = new PlannerComment(plannerId, otherUser.getId(), "Parent", null, 0);
            parentComment.setId(50L);
            parentComment.setPublicId(parentPublicId);
            parentComment.setCreatedAt(Instant.now());

            CreateCommentRequest request = new CreateCommentRequest("Reply comment", parentPublicId);
            AtomicReference<PlannerComment> persisted = new AtomicReference<>();
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(commentRepository.findByPublicId(parentPublicId))
                    .thenReturn(Optional.of(parentComment));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = i.getArgument(0);
                        c.setId(101L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        persisted.set(c);
                        return c;
                    });

            // Act
            UUID deviceId = UUID.randomUUID();
            CreateCommentResponse response = commandService.createComment(plannerId, testUser.getId(), deviceId, request);

            // Assert
            PlannerComment stored = persisted.get();
            assertEquals("Reply comment", stored.getContent());
            assertEquals(1, stored.getDepth());
            assertEquals(50L, stored.getParentCommentId());
            assertEquals(stored.getPublicId(), response.id());
            assertEquals(stored.getCreatedAt(), response.createdAt());
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for non-existent planner")
        void createComment_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Test", null);
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(false);

            // Act & Assert
            UUID deviceId = UUID.randomUUID();
            assertThrows(PlannerNotFoundException.class,
                    () -> commandService.createComment(plannerId, testUser.getId(), deviceId, request));
        }

        @Test
        @DisplayName("Throws CommentNotFoundException for non-existent parent")
        void createComment_WhenParentNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentParentId = UUID.randomUUID();
            CreateCommentRequest request = new CreateCommentRequest("Reply", nonExistentParentId);
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(commentRepository.findByPublicId(nonExistentParentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            UUID deviceId = UUID.randomUUID();
            assertThrows(CommentNotFoundException.class,
                    () -> commandService.createComment(plannerId, testUser.getId(), deviceId, request));
        }

        @Test
        @DisplayName("Allows nesting below MAX_DEPTH without flattening")
        void createComment_WhenDeepNesting_AllowedWithoutFlattening() {
            // Arrange
            // depth 5 sits well below MAX_DEPTH (127), so no flattening occurs
            UUID parentPublicId = UUID.randomUUID();
            PlannerComment depth5Parent = new PlannerComment(plannerId, otherUser.getId(), "Deep", 40L, 5);
            depth5Parent.setId(50L);
            depth5Parent.setPublicId(parentPublicId);
            depth5Parent.setCreatedAt(Instant.now());
            depth5Parent.setParentCommentId(40L);

            CreateCommentRequest request = new CreateCommentRequest("Very deep reply", parentPublicId);
            AtomicReference<PlannerComment> persisted = new AtomicReference<>();
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(commentRepository.findByPublicId(parentPublicId))
                    .thenReturn(Optional.of(depth5Parent));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = i.getArgument(0);
                        c.setId(102L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        persisted.set(c);
                        return c;
                    });

            // Act
            UUID deviceId = UUID.randomUUID();
            CreateCommentResponse response = commandService.createComment(plannerId, testUser.getId(), deviceId, request);

            // Assert
            PlannerComment stored = persisted.get();
            assertEquals(6, stored.getDepth());
            // 40L is the parent's own parent: a flattening implementation reparents the reply there.
            assertEquals(50L, stored.getParentCommentId());
            assertEquals(stored.getPublicId(), response.id());
            assertEquals(stored.getCreatedAt(), response.createdAt());
        }
    }

    @Nested
    @DisplayName("updateComment Tests")
    class UpdateCommentTests {

        @Test
        @DisplayName("Author can edit their comment")
        void updateComment_WhenByAuthor_Succeeds() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);
            comment.setCreatedAt(Instant.now());

            UpdateCommentRequest request = new UpdateCommentRequest("Updated content");
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act
            UpdateCommentResponse response = commandService.updateComment(commentPublicId, testUser.getId(), request);

            // Assert
            assertEquals("Updated content", comment.getContent());
            assertNotNull(comment.getEditedAt());
            assertEquals(comment.getEditedAt(), response.editedAt());
        }

        @Test
        @DisplayName("Non-author cannot edit comment")
        void updateComment_WhenByNonAuthor_ThrowsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);

            UpdateCommentRequest request = new UpdateCommentRequest("Hacked content");
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> commandService.updateComment(commentPublicId, otherUser.getId(), request));
        }

        @Test
        @DisplayName("Cannot edit deleted comment")
        void updateComment_WhenDeletedComment_ThrowsException() {
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
                    () -> commandService.updateComment(commentPublicId, testUser.getId(), request));
        }
    }

    @Nested
    @DisplayName("deleteComment Tests")
    class DeleteCommentTests {

        @Test
        @DisplayName("Author can delete their comment")
        void deleteComment_WhenByAuthor_Succeeds() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "To delete", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);
            comment.setCreatedAt(Instant.now());

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act
            commandService.deleteComment(commentPublicId, testUser.getId());

            // Assert
            assertTrue(comment.isDeleted());
            assertEquals("", comment.getContent()); // Content cleared on soft delete
        }

        @Test
        @DisplayName("Non-author cannot delete comment")
        void deleteComment_WhenByNonAuthor_ThrowsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Protected", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertThrows(CommentForbiddenException.class,
                    () -> commandService.deleteComment(commentPublicId, otherUser.getId()));
            assertFalse(comment.isDeleted());
        }

        @Test
        @DisplayName("Deleting already deleted comment is idempotent")
        void deleteComment_WhenAlreadyDeleted_Idempotent() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), null, null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);
            comment.softDelete();

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act
            commandService.deleteComment(commentPublicId, testUser.getId());

            // Assert - the counter must not drop a second time for an already-withdrawn comment
            verify(plannerStatsRepository, never()).decrementCommentCount(any());
        }
    }

    @Nested
    @DisplayName("getCommentTree Tests")
    class GetCommentTreeTests {

        @Test
        @DisplayName("Returns empty list for planner with no comments")
        void getCommentTree_WhenNoComments_ReturnsEmptyList() {
            // Arrange
            when(plannerRepository.findAggregate(plannerId))
                    .thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findByPlannerId(plannerId))
                    .thenReturn(Collections.emptyList());

            // Act
            List<CommentTreeNode> comments = queryService.getCommentTree(plannerId, testUser.getId());

            // Assert
            assertTrue(comments.isEmpty());
        }

        @Test
        @DisplayName("A deactivated author is unattributed while an active author is named")
        void getCommentTree_WhenAuthorAccountDeactivated_OmitsTheAuthorName() {
            User deactivated = User.builder()
                    .id(3L)
                    .email("gone@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("gone-123")
                    .usernameEpithet("GONE")
                    .usernameSuffix("gon01")
                    .role(UserRole.NORMAL)
                    .build();
            deactivated.softDelete(Instant.now().plus(Duration.ofDays(30)));

            PlannerComment byActive =
                    new PlannerComment(plannerId, otherUser.getId(), "still here", null, 0);
            byActive.setId(10L);
            byActive.setPublicId(UUID.randomUUID());
            byActive.setCreatedAt(Instant.now());

            PlannerComment byDeactivated =
                    new PlannerComment(plannerId, deactivated.getId(), "also still here", null, 0);
            byDeactivated.setId(11L);
            byDeactivated.setPublicId(UUID.randomUUID());
            byDeactivated.setCreatedAt(Instant.now());

            when(plannerRepository.findAggregate(plannerId)).thenReturn(Optional.of(publishedPlanner));
            when(commentRepository.findByPlannerId(plannerId))
                    .thenReturn(List.of(byActive, byDeactivated));
            when(userService.findAllByIds(anyCollection()))
                    .thenReturn(List.of(otherUser, deactivated));

            List<CommentTreeNode> comments = queryService.getCommentTree(plannerId, testUser.getId());

            CommentTreeNode active = comments.stream()
                    .filter(c -> c.id().equals(byActive.getPublicId())).findFirst().orElseThrow();
            CommentTreeNode hidden = comments.stream()
                    .filter(c -> c.id().equals(byDeactivated.getPublicId())).findFirst().orElseThrow();

            assertEquals("OTHER", active.authorEpithet(), "an active author is still named");
            assertEquals("", hidden.authorEpithet(), "a deactivated author is not named");
            assertEquals("", hidden.authorSuffix(), "nor is their suffix");
            assertEquals("also still here", hidden.content(),
                    "the comment itself survives so the thread keeps its shape");
        }

        @Test
        @DisplayName("Throws PlannerNotFoundException for non-existent planner")
        void getCommentTree_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            when(plannerRepository.findAggregate(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(PlannerNotFoundException.class,
                    () -> queryService.getCommentTree(plannerId, testUser.getId()));
        }
    }

    @Nested
    @DisplayName("Restriction Enforcement Tests")
    class RestrictionEnforcementTests {

        @Test
        @DisplayName("Timed-out user cannot create comment")
        void createComment_WhenTimedOutUser_ThrowsUserTimedOutException() {
            // Arrange
            java.time.Instant futureTimeout = java.time.Instant.now().plusSeconds(3600);
            testUser.setTimeoutUntil(futureTimeout);

            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);

            CreateCommentRequest request = new CreateCommentRequest("Test comment", null);

            // Act & Assert
            UserTimedOutException exception = assertThrows(
                    UserTimedOutException.class,
                    () -> commandService.createComment(plannerId, testUser.getId(), UUID.randomUUID(), request)
            );
            assertEquals(testUser.getId(), exception.getUserId());
            verify(commentRepository, never()).insert(any());
        }

        @Test
        @DisplayName("Banned user cannot create comment")
        void createComment_WhenBannedUser_ThrowsUserBannedException() {
            // Arrange
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);

            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);

            CreateCommentRequest request = new CreateCommentRequest("Test comment", null);

            // Act & Assert
            UserBannedException exception = assertThrows(
                    UserBannedException.class,
                    () -> commandService.createComment(plannerId, testUser.getId(), UUID.randomUUID(), request)
            );
            assertEquals(testUser.getId(), exception.getUserId());
            verify(commentRepository, never()).insert(any());
        }

        @Test
        @DisplayName("Timed-out user may still vote on a comment")
        void toggleUpvote_WhenTimedOutUser_IsNotRestricted() {
            // Arrange
            java.time.Instant futureTimeout = java.time.Instant.now().plusSeconds(3600);
            testUser.setTimeoutUntil(futureTimeout);

            UUID commentId = UUID.randomUUID();
            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);

            // Act & Assert - only a ban withdraws engagement
            assertThrows(
                    CommentNotFoundException.class,
                    () -> engagementService.toggleUpvote(commentId, testUser.getId())
            );
            verify(commentVoteRepository, never()).insert(any());
        }

        @Test
        @DisplayName("Banned user cannot vote on comment")
        void toggleUpvote_WhenBannedUser_ThrowsUserBannedException() {
            // Arrange
            testUser.setBannedAt(java.time.Instant.now());
            testUser.setBannedBy(1L);

            UUID commentId = UUID.randomUUID();
            when(userService.findById(testUser.getId()))
                    .thenReturn(testUser);

            // Act & Assert
            assertThrows(
                    UserBannedException.class,
                    () -> engagementService.toggleUpvote(commentId, testUser.getId())
            );
            verify(commentVoteRepository, never()).insert(any());
        }

        @Test
        @DisplayName("The vote response carries the counter re-read after the atomic increment")
        void toggleUpvote_WhenVoteCast_ReportsTheReReadCount() {
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment stale = new PlannerComment(plannerId, otherUser.getId(), "text", null, 0);
            PlannerComment incremented = new PlannerComment(plannerId, otherUser.getId(), "text", null, 0);
            org.springframework.test.util.ReflectionTestUtils.setField(stale, "id", 10L);
            org.springframework.test.util.ReflectionTestUtils.setField(stale, "publicId", commentPublicId);
            org.springframework.test.util.ReflectionTestUtils.setField(incremented, "id", 10L);
            org.springframework.test.util.ReflectionTestUtils.setField(incremented, "upvoteCount", 8);

            when(userService.findById(testUser.getId())).thenReturn(testUser);
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(stale));
            when(commentVoteRepository.existsById(any())).thenReturn(false);
            when(commentRepository.findById(10L)).thenReturn(Optional.of(incremented));

            var response = engagementService.toggleUpvote(commentPublicId, testUser.getId());

            assertEquals(8, response.upvoteCount());
            verify(commentRepository).incrementUpvoteCount(10L);
        }
    }
}
