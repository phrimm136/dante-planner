package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentEngagementService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.planner.precondition.VoteUniquenessValidator;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.danteplanner.backend.shared.util.CommentConstants;


import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.comment.dto.UpdateCommentResponse;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.repository.PlannerCommentRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentVoteRepository;
import org.danteplanner.backend.planner.api.PlannerThread;
import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;

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
    private DomainEventRepository domainEventRepository;


    // A comment write records one domain event and nothing else; who hears about it is decided by
    // the arm that dispatches the row, so no create test here can assert delivery.

    private final FakeUsers users = new FakeUsers();

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
                commentRepository, commentVoteRepository, users,
                new PlannerAccessGuard(users, plannerRepository),
                new CommentAccessValidator());
        commandService = new CommentCommandService(
                commentRepository, queryService,
                new DomainEventRecorder(domainEventRepository, new ObjectMapper(), event -> { }),
                new PlannerAccessGuard(users, plannerRepository),
                new PlannerStatsService(plannerStatsRepository),
                new CommentAccessValidator(), new CommentAuthorshipValidator(),
                new CommentStateValidator());
        engagementService = new CommentEngagementService(
                commentRepository, commentVoteRepository, queryService,
                null, new CommentAuthorshipValidator(),
                new CommentStateValidator(), new VoteUniquenessValidator());

        testUser = TestDataFactory.unsavedUser(UserRole.NORMAL);

        otherUser = TestDataFactory.user()
                .usernameEpithet("OTHER")
                .build();
        users.add(testUser);
        users.add(otherUser);

        plannerId = UUID.randomUUID();
        publishedPlanner = TestDataFactory.planner(testUser)
                .id(plannerId)
                .category("5F")
                .content("{}")
                .contentVersion(1)
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .published(true)
                .build();
    }

    private void givenEventRowsAreStored() {
        when(domainEventRepository.insert(any(DomainEvent.class))).thenAnswer(invocation -> {
            DomainEvent row = invocation.getArgument(0);
            ReflectionTestUtils.setField(row, "id", 1L);
            return row;
        });
    }

    @Nested
    class CreateCommentTests {

        @Test
        void createComment_WhenTopLevel_Succeeds() {
            givenEventRowsAreStored();
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Test comment", null);
            AtomicReference<PlannerComment> persisted = new AtomicReference<>();
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
            assertThat(stored.getPlannerId()).isEqualTo(plannerId);
            assertThat(stored.getUserId()).isEqualTo(testUser.getId());
            assertThat(stored.getContent()).isEqualTo("Test comment");
            assertThat(stored.getDepth()).isEqualTo(0);
            assertThat(stored.getParentCommentId()).isNull();
            assertThat(response.id()).isEqualTo(stored.getPublicId());
            assertThat(response.createdAt()).isEqualTo(stored.getCreatedAt());
        }

        @Test
        void createComment_WhenReply_Succeeds() {
            givenEventRowsAreStored();
            // Arrange
            UUID parentPublicId = UUID.randomUUID();
            PlannerComment parentComment = new PlannerComment(plannerId, otherUser.getId(), "Parent", null, 0);
            parentComment.setId(50L);
            parentComment.setPublicId(parentPublicId);
            parentComment.setCreatedAt(Instant.now());

            CreateCommentRequest request = new CreateCommentRequest("Reply comment", parentPublicId);
            AtomicReference<PlannerComment> persisted = new AtomicReference<>();
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
            assertThat(stored.getContent()).isEqualTo("Reply comment");
            assertThat(stored.getDepth()).isEqualTo(1);
            assertThat(stored.getParentCommentId()).isEqualTo(50L);
            assertThat(response.id()).isEqualTo(stored.getPublicId());
            assertThat(response.createdAt()).isEqualTo(stored.getCreatedAt());
        }

        @Test
        void createComment_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Test", null);
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(false);

            // Act & Assert
            UUID deviceId = UUID.randomUUID();
            assertRejected(() -> commandService.createComment(plannerId, testUser.getId(), deviceId, request))
                    .withCode("PLANNER_NOT_FOUND");
        }

        @Test
        void createComment_WhenParentNotFound_ThrowsException() {
            // Arrange
            UUID nonExistentParentId = UUID.randomUUID();
            CreateCommentRequest request = new CreateCommentRequest("Reply", nonExistentParentId);
            when(plannerRepository.existsPublishedById(plannerId)).thenReturn(true);
            when(commentRepository.findByPublicId(nonExistentParentId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            UUID deviceId = UUID.randomUUID();
            assertRejected(() -> commandService.createComment(plannerId, testUser.getId(), deviceId, request))
                    .withCode("COMMENT_NOT_FOUND");
        }

        @Test
        void createComment_WhenDeepNesting_AllowedWithoutFlattening() {
            givenEventRowsAreStored();
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
            assertThat(stored.getDepth()).isEqualTo(6);
            // 40L is the parent's own parent: a flattening implementation reparents the reply there.
            assertThat(stored.getParentCommentId()).isEqualTo(50L);
            assertThat(response.id()).isEqualTo(stored.getPublicId());
            assertThat(response.createdAt()).isEqualTo(stored.getCreatedAt());
        }
    }

    @Nested
    class UpdateCommentTests {

        @Test
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
            assertThat(comment.getContent()).isEqualTo("Updated content");
            assertThat(comment.getEditedAt()).isNotNull();
            assertThat(response.editedAt()).isEqualTo(comment.getEditedAt());
        }

        @Test
        void updateComment_WhenByNonAuthor_ThrowsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Original", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);

            UpdateCommentRequest request = new UpdateCommentRequest("Hacked content");
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertRejected(() -> commandService.updateComment(commentPublicId, otherUser.getId(), request))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Only the author can edit this comment"));
        }

        @Test
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
            assertRejected(() -> commandService.updateComment(commentPublicId, testUser.getId(), request))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Cannot edit a deleted comment"));
        }
    }

    @Nested
    class DeleteCommentTests {

        @Test
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
            assertThat(comment.isDeleted()).isTrue();
            assertThat(comment.getContent()).isEqualTo(""); // Content cleared on soft delete
        }

        @Test
        void deleteComment_WhenByNonAuthor_ThrowsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment comment = new PlannerComment(plannerId, testUser.getId(), "Protected", null, 0);
            comment.setId(100L);
            comment.setPublicId(commentPublicId);

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertRejected(() -> commandService.deleteComment(commentPublicId, otherUser.getId()))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Only the author can delete this comment"));
            assertThat(comment.isDeleted()).isFalse();
        }

        @Test
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
    class GetCommentTreeTests {

        @Test
        void getCommentTree_WhenNoComments_ReturnsEmptyList() {
            // Arrange
            when(plannerRepository.findThread(plannerId))
                    .thenReturn(Optional.of(new PlannerThread(plannerId, publishedPlanner.getUserId(), publishedPlanner.isPublished(), false)));
            when(commentRepository.findByPlannerId(plannerId))
                    .thenReturn(Collections.emptyList());

            // Act
            List<CommentTreeNode> comments = queryService.getCommentTree(plannerId, testUser.getId());

            // Assert
            assertThat(comments).isEmpty();
        }

        @Test
        void getCommentTree_WhenAuthorAccountDeactivated_OmitsTheAuthorName() {
            User deactivated = TestDataFactory.unsavedUser(UserRole.NORMAL);
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

            when(plannerRepository.findThread(plannerId))
                    .thenReturn(Optional.of(new PlannerThread(plannerId, publishedPlanner.getUserId(), publishedPlanner.isPublished(), false)));
            when(commentRepository.findByPlannerId(plannerId))
                    .thenReturn(List.of(byActive, byDeactivated));
            users.add(deactivated);

            List<CommentTreeNode> comments = queryService.getCommentTree(plannerId, testUser.getId());

            CommentTreeNode active = comments.stream()
                    .filter(c -> c.id().equals(byActive.getPublicId())).findFirst().orElseThrow();
            CommentTreeNode hidden = comments.stream()
                    .filter(c -> c.id().equals(byDeactivated.getPublicId())).findFirst().orElseThrow();

            assertThat(active.authorEpithet()).as("an active author is still named").isEqualTo("OTHER");
            assertThat(hidden.authorEpithet()).as("a deactivated author is not named").isEqualTo("");
            assertThat(hidden.authorSuffix()).as("nor is their suffix").isEqualTo("");
            assertThat(hidden.content())
                    .as("a deactivated author's body is hidden under the same author-lifecycle rule as the name")
                    .isEqualTo(CommentConstants.DELETED_CONTENT);
        }

        @Test
        void getCommentTree_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            when(plannerRepository.findThread(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> queryService.getCommentTree(plannerId, testUser.getId()))
                    .withCode("PLANNER_NOT_FOUND");
        }
    }

    @Nested
    class ToggleUpvoteTests {

        @Test
        void toggleUpvote_WhenVoteCast_ReportsTheReReadCount() {
            UUID commentPublicId = UUID.randomUUID();
            PlannerComment stale = new PlannerComment(plannerId, otherUser.getId(), "text", null, 0);
            PlannerComment incremented = new PlannerComment(plannerId, otherUser.getId(), "text", null, 0);
            org.springframework.test.util.ReflectionTestUtils.setField(stale, "id", 10L);
            org.springframework.test.util.ReflectionTestUtils.setField(stale, "publicId", commentPublicId);
            org.springframework.test.util.ReflectionTestUtils.setField(incremented, "id", 10L);
            org.springframework.test.util.ReflectionTestUtils.setField(incremented, "upvoteCount", 8);

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(stale));
            when(commentVoteRepository.existsById(any())).thenReturn(false);
            when(commentRepository.findById(10L)).thenReturn(Optional.of(incremented));

            var response = engagementService.toggleUpvote(commentPublicId, testUser.getId());

            assertThat(response.upvoteCount()).isEqualTo(8);
            verify(commentRepository).incrementUpvoteCount(10L);
        }
    }
}
