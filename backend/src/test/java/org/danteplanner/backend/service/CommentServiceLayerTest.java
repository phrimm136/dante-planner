package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.api.Visibility;
import org.danteplanner.backend.shared.dto.NotificationSetting;
import org.danteplanner.backend.shared.freshness.FreshnessDeclarations;
import org.danteplanner.backend.shared.freshness.FreshnessDeclaration;
import org.danteplanner.backend.comment.exception.CommentVoteAlreadyExistsException;
import org.danteplanner.backend.comment.dto.ToggleNotificationResponse;
import org.danteplanner.backend.comment.repository.PlannerCommentStatsRepository;
import org.danteplanner.backend.comment.repository.PlannerCommentContentRepository;
import org.danteplanner.backend.support.CommentRows;
import org.danteplanner.backend.comment.repository.CommentRow;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.shared.freshness.FreshnessScope;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentEngagementService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.shared.outbox.entity.DomainEvent;
import org.danteplanner.backend.shared.outbox.repository.DomainEventRepository;
import org.danteplanner.backend.shared.outbox.service.DomainEventRecorder;
import org.danteplanner.backend.shared.security.Viewer;
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
import org.danteplanner.backend.support.LogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private static final long PLANNER_OWNER_ID = 41L;

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private PlannerCommentContentRepository contentRepository;

    @Mock
    private PlannerCommentStatsRepository statsRepository;

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
    private StoredPlanner publishedPlanner;
    private UUID plannerId;

    @BeforeEach
    void setUp() {
        queryService = new CommentQueryService(
                commentRepository, commentVoteRepository, users,
                new PlannerAccessGuard(users, plannerRepository),
                new CommentAccessValidator());
        commandService = new CommentCommandService(
                commentRepository, contentRepository, statsRepository, queryService,
                new DomainEventRecorder(domainEventRepository, new ObjectMapper(), event -> { }),
                new PlannerAccessGuard(users, plannerRepository),
                new PlannerStatsService(plannerStatsRepository),
                Clock.systemUTC());
        engagementService = new CommentEngagementService(
                contentRepository, statsRepository, commentVoteRepository, queryService,
                null);

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
            when(plannerRepository.findThread(plannerId))
                .thenReturn(Optional.of(new PlannerThread(plannerId, PLANNER_OWNER_ID, Visibility.SHOWN)));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = CommentRows.insertedAs(100L).answer(i);
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
            verify(contentRepository).insertContent(stored.getId(), "Test comment");
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
            CommentRow parentComment = CommentRows.live(50L, parentPublicId, plannerId, otherUser.getId(), "Parent", null, 0);

            CreateCommentRequest request = new CreateCommentRequest("Reply comment", parentPublicId);
            AtomicReference<PlannerComment> persisted = new AtomicReference<>();
            when(plannerRepository.findThread(plannerId))
                .thenReturn(Optional.of(new PlannerThread(plannerId, PLANNER_OWNER_ID, Visibility.SHOWN)));
            when(commentRepository.findByPublicId(parentPublicId))
                    .thenReturn(Optional.of(parentComment));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = CommentRows.insertedAs(101L).answer(i);
                        persisted.set(c);
                        return c;
                    });

            // Act
            UUID deviceId = UUID.randomUUID();
            CreateCommentResponse response = commandService.createComment(plannerId, testUser.getId(), deviceId, request);

            // Assert
            PlannerComment stored = persisted.get();
            verify(contentRepository).insertContent(stored.getId(), "Reply comment");
            assertThat(stored.getDepth()).isEqualTo(1);
            assertThat(stored.getParentCommentId()).isEqualTo(50L);
            assertThat(response.id()).isEqualTo(stored.getPublicId());
            assertThat(response.createdAt()).isEqualTo(stored.getCreatedAt());
        }

        @Test
        void createComment_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            CreateCommentRequest request = new CreateCommentRequest("Test", null);
            when(plannerRepository.findThread(plannerId)).thenReturn(Optional.empty());

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
            when(plannerRepository.findThread(plannerId))
                .thenReturn(Optional.of(new PlannerThread(plannerId, PLANNER_OWNER_ID, Visibility.SHOWN)));
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
            CommentRow depth5Parent = CommentRows.live(50L, parentPublicId, plannerId, otherUser.getId(), "Deep", 40L, 5);

            CreateCommentRequest request = new CreateCommentRequest("Very deep reply", parentPublicId);
            AtomicReference<PlannerComment> persisted = new AtomicReference<>();
            when(plannerRepository.findThread(plannerId))
                .thenReturn(Optional.of(new PlannerThread(plannerId, PLANNER_OWNER_ID, Visibility.SHOWN)));
            when(commentRepository.findByPublicId(parentPublicId))
                    .thenReturn(Optional.of(depth5Parent));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(i -> {
                        PlannerComment c = CommentRows.insertedAs(102L).answer(i);
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
            CommentRow comment = CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "Original", null, 0);

            UpdateCommentRequest request = new UpdateCommentRequest("Updated content");
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));
            when(contentRepository.edit(any(), any(), any())).thenReturn(1);

            // Act
            UpdateCommentResponse response = commandService.updateComment(commentPublicId, testUser.getId(), request);

            // Assert
            assertThat(response.editedAt()).isNotNull();
            verify(contentRepository).edit(100L, "Updated content", response.editedAt());
        }

        @Test
        void updateComment_WhenTheEditMatchesNoLiveRow_IsRefusedForTheDeletionAndDeclaresNothing() {
            UUID commentPublicId = UUID.randomUUID();
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(
                    CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "Original", null, 0)));
            when(contentRepository.edit(any(), any(), any())).thenReturn(0);
            FreshnessDeclarations.begin();
            try {
                assertRejected(() -> commandService.updateComment(
                        commentPublicId, testUser.getId(), new UpdateCommentRequest("Updated")))
                        .withCode("COMMENT_FORBIDDEN")
                        .satisfies(rejection -> assertThat(rejection.getMessage())
                                .isEqualTo("Cannot edit a deleted comment"));
                assertThat(FreshnessDeclarations.declaration()).isInstanceOf(FreshnessDeclaration.Undeclared.class);
            } finally {
                FreshnessDeclarations.clear();
            }
        }

        @Test
        void updateComment_WhenByNonAuthor_ThrowsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            CommentRow comment = CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "Original", null, 0);

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
            CommentRow comment = CommentRows.deleted(
                    CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "Original", null, 0),
                    Instant.now());

            UpdateCommentRequest request = new UpdateCommentRequest("Updated");
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertRejected(() -> commandService.updateComment(commentPublicId, testUser.getId(), request))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Cannot edit a deleted comment"));
            verifyNoInteractions(contentRepository);
        }
    }

    @Nested
    class DeleteCommentTests {

        @Test
        void deleteComment_WhenByAuthor_Succeeds() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            CommentRow comment = CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "To delete", null, 0);

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            when(contentRepository.withdraw(eq(100L), eq(CommentConstants.DELETED_CONTENT), any())).thenReturn(1);

            // Act
            commandService.deleteComment(commentPublicId, testUser.getId());

            // Assert
            verify(plannerStatsRepository).decrementCommentCount(plannerId);
        }

        @Test
        void deleteComment_WhenTheWithdrawalMatchesNoLiveRow_LowersNoCounterDeclaresNothingAndLogsNothing() {
            UUID commentPublicId = UUID.randomUUID();
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(
                    CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "To delete", null, 0)));
            when(contentRepository.withdraw(eq(100L), eq(CommentConstants.DELETED_CONTENT), any())).thenReturn(0);
            FreshnessDeclarations.begin();
            try {
                List<ILoggingEvent> logged = LogCapture.during(
                        () -> commandService.deleteComment(commentPublicId, testUser.getId()),
                        Level.INFO, CommentCommandService.class);

                assertThat(logged).isEmpty();
                assertThat(FreshnessDeclarations.declaration()).isInstanceOf(FreshnessDeclaration.Undeclared.class);
            } finally {
                FreshnessDeclarations.clear();
            }
            verifyNoInteractions(plannerStatsRepository);
        }

        @Test
        void deleteComment_WhenByNonAuthor_ThrowsException() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            CommentRow comment = CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "Protected", null, 0);

            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            // Act & Assert
            assertRejected(() -> commandService.deleteComment(commentPublicId, otherUser.getId()))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Only the author can delete this comment"));
            verifyNoInteractions(contentRepository);
        }

        @Test
        void deleteComment_WhenAlreadyDeleted_Idempotent() {
            // Arrange
            UUID commentPublicId = UUID.randomUUID();
            CommentRow comment = CommentRows.deleted(
                    CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "", null, 0),
                    Instant.now());

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
                    .thenReturn(Optional.of(new PlannerThread(plannerId, publishedPlanner.userId(), publishedPlanner.publication().isPublished() ? Visibility.SHOWN : Visibility.HIDDEN)));
            when(commentRepository.findByPlannerId(plannerId))
                    .thenReturn(Collections.emptyList());

            // Act
            List<CommentTreeNode> comments = queryService.getCommentTree(plannerId, new Viewer.Member(testUser.getId())).value();

            // Assert
            assertThat(comments).isEmpty();
        }

        @Test
        void getCommentTree_WhenAuthorAccountDeactivated_OmitsTheAuthorName() {
            User deactivated = TestDataFactory.unsavedUser(UserRole.NORMAL);
            TestDataFactory.softDelete(deactivated, Instant.now(), Instant.now().plus(Duration.ofDays(30)));

            CommentRow byActive = CommentRows.live(10L, UUID.randomUUID(), plannerId, otherUser.getId(), "still here", null, 0);

            CommentRow byDeactivated = CommentRows.live(11L, UUID.randomUUID(), plannerId, deactivated.getId(), "also still here", null, 0);

            when(plannerRepository.findThread(plannerId))
                    .thenReturn(Optional.of(new PlannerThread(plannerId, publishedPlanner.userId(), publishedPlanner.publication().isPublished() ? Visibility.SHOWN : Visibility.HIDDEN)));
            when(commentRepository.findByPlannerId(plannerId))
                    .thenReturn(List.of(byActive, byDeactivated));
            users.add(deactivated);

            List<CommentTreeNode> comments = queryService.getCommentTree(plannerId, new Viewer.Member(testUser.getId())).value();

            CommentTreeNode active = comments.stream()
                    .filter(c -> c.id().equals(byActive.publicId())).findFirst().orElseThrow();
            CommentTreeNode hidden = comments.stream()
                    .filter(c -> c.id().equals(byDeactivated.publicId())).findFirst().orElseThrow();

            assertThat(active.authorEpithet()).as("an active author is still named").isEqualTo("OTHER");
            assertThat(hidden.authorEpithet()).as("a deactivated author is not named").isEqualTo("");
            assertThat(hidden.authorSuffix()).as("nor is their suffix").isEqualTo("");
            assertThat(hidden.content())
                    .as("a deactivated author's body is hidden under the same author-lifecycle rule as the name")
                    .isEqualTo(CommentConstants.DELETED_CONTENT);
        }

        @Test
        void getCommentTree_WhenOthersCommented_ScopesThePlannerItsOwnerAndEachCommenterButTheSentinel() {
            List<CommentRow> thread = new ArrayList<>();
            for (Long authorId : List.of(2L, 3L, Users.SENTINEL_USER_ID)) {
                CommentRow comment = CommentRows.live(100L + thread.size(), UUID.randomUUID(), plannerId, authorId, "by " + authorId, null, 0);
                thread.add(comment);
            }
            when(plannerRepository.findThread(plannerId))
                    .thenReturn(Optional.of(new PlannerThread(plannerId, 1L, Visibility.SHOWN)));
            when(commentRepository.findByPlannerId(plannerId)).thenReturn(thread);

            assertThat(queryService.getCommentTree(plannerId, new Viewer.Guest()).scopes()).containsExactlyInAnyOrder(
                    new FreshnessScope.Planner(plannerId), new FreshnessScope.User(1L),
                    new FreshnessScope.User(2L), new FreshnessScope.User(3L));
        }

        @Test
        void getCommentTree_WhenPlannerNotFound_ThrowsException() {
            // Arrange
            when(plannerRepository.findThread(plannerId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertRejected(() -> queryService.getCommentTree(plannerId, new Viewer.Member(testUser.getId())))
                    .withCode("PLANNER_NOT_FOUND");
        }
    }

    @Nested
    class ToggleUpvoteTests {

        @Test
        void toggleUpvote_WhenVoteCast_ReportsTheReReadCount() {
            UUID commentPublicId = UUID.randomUUID();
            givenALiveComment(commentPublicId);
            when(commentVoteRepository.insertIgnore(10L, testUser.getId(), "UP")).thenReturn(1);
            when(statsRepository.upvoteCountOf(10L)).thenReturn(8);

            var response = engagementService.toggleUpvote(commentPublicId, testUser.getId());

            assertThat(response.upvoteCount()).isEqualTo(8);
            verify(statsRepository).incrementUpvoteCount(10L);
        }

        @Test
        void toggleUpvote_WhenTheVoteIsAlreadyStored_IsRefusedAndCountsNothing() {
            UUID commentPublicId = UUID.randomUUID();
            givenALiveComment(commentPublicId);
            when(commentVoteRepository.insertIgnore(10L, testUser.getId(), "UP")).thenReturn(0);

            assertRejected(() -> engagementService.toggleUpvote(commentPublicId, testUser.getId()))
                    .withCode("VOTE_ALREADY_EXISTS")
                    .isInstanceOfSatisfying(CommentVoteAlreadyExistsException.class,
                            refusal -> assertThat(refusal.getCommentPublicId()).isEqualTo(commentPublicId));
            verifyNoInteractions(statsRepository);
        }

        private void givenALiveComment(UUID commentPublicId) {
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(
                    CommentRows.live(10L, commentPublicId, plannerId, otherUser.getId(), "text", null, 0)));
        }

        @Test
        void toggleUpvote_WhenCommentDeleted_IsRefusedBeforeTheVoteIsRead() {
            UUID commentPublicId = UUID.randomUUID();
            CommentRow comment = CommentRows.deleted(
                    CommentRows.live(100L, commentPublicId, plannerId, otherUser.getId(), "text", null, 0),
                    Instant.now());
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            assertRejected(() -> engagementService.toggleUpvote(commentPublicId, testUser.getId()))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage()).isEqualTo("Cannot vote on a deleted comment"));
            verifyNoInteractions(commentVoteRepository);
        }
    }

    @Nested
    class ToggleNotificationTests {

        @Test
        void toggleNotification_WhenByNonAuthor_IsRefused() {
            UUID commentPublicId = UUID.randomUUID();
            CommentRow comment = CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "text", null, 0);
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));

            assertRejected(() -> engagementService.toggleNotification(commentPublicId, otherUser.getId(), NotificationSetting.OFF))
                    .withCode("COMMENT_FORBIDDEN")
                    .satisfies(rejection -> assertThat(rejection.getMessage())
                            .isEqualTo("Only the author can toggle notification settings"));
            verifyNoInteractions(contentRepository);
        }

        @Test
        void toggleNotification_WhenTheAuthorsCommentIsDeleted_StillStoresTheChoiceAndLogsIt() {
            UUID commentPublicId = UUID.randomUUID();
            CommentRow comment = CommentRows.deleted(
                    CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "text", null, 0),
                    Instant.now());
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(comment));
            when(contentRepository.setAuthorNotifications(100L, false)).thenReturn(1);

            List<ILoggingEvent> logged = LogCapture.during(
                    () -> engagementService.toggleNotification(commentPublicId, testUser.getId(), NotificationSetting.OFF),
                    Level.INFO, CommentEngagementService.class);

            assertThat(logged).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                    "User " + testUser.getId() + " toggled notifications off for comment " + commentPublicId);
        }

        @Test
        void toggleNotification_WhenTurnedOn_EnablesThem() {
            UUID commentPublicId = UUID.randomUUID();
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(
                    CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "text", null, 0)));
            when(contentRepository.setAuthorNotifications(100L, true)).thenReturn(1);

            assertThat(engagementService.toggleNotification(commentPublicId, testUser.getId(), NotificationSetting.ON))
                    .isEqualTo(new ToggleNotificationResponse(true));
        }

        @Test
        void toggleNotification_WhenNoRowMatches_IsNotFound() {
            UUID commentPublicId = UUID.randomUUID();
            when(commentRepository.findByPublicId(commentPublicId)).thenReturn(Optional.of(
                    CommentRows.live(100L, commentPublicId, plannerId, testUser.getId(), "text", null, 0)));
            when(contentRepository.setAuthorNotifications(100L, false)).thenReturn(0);

            assertRejected(() -> engagementService.toggleNotification(commentPublicId, testUser.getId(), NotificationSetting.OFF))
                    .withCode("COMMENT_NOT_FOUND");
        }
    }
}
