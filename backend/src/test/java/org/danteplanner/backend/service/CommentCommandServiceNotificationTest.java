package org.danteplanner.backend.service;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.danteplanner.backend.planner.entity.PlannerType;
import org.danteplanner.backend.planner.entity.PlannerStatus;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.comment.entity.PlannerComment;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.comment.validation.CommentAccessValidator;
import org.danteplanner.backend.comment.validation.CommentAuthorshipValidator;
import org.danteplanner.backend.comment.validation.CommentStateValidator;
import org.danteplanner.backend.planner.service.PlannerStatsService;
import org.danteplanner.backend.comment.event.CommentCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;

import org.danteplanner.backend.notification.service.NotificationDispatchService;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.danteplanner.backend.planner.repository.PlannerStatsRepository;

/**
 * Unit tests for CommentCommandService notification flag behavior.
 * Tests that notifications respect ownerNotificationsEnabled and authorNotificationsEnabled settings.
 */
@ExtendWith(MockitoExtension.class)
class CommentCommandServiceNotificationTest {

    @Mock
    private PlannerCommentRepository commentRepository;

    @Mock
    private PlannerCommentVoteRepository commentVoteRepository;

    @Mock
    private PlannerRepository plannerRepository;

    @Mock
    private PlannerStatsRepository plannerStatsRepository;

    @Mock
    private UserService userService;

    @Mock
    private NotificationDispatchService notificationDispatchService;


    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CommentCommandService service;

    private static final UUID PLANNER_ID = UUID.randomUUID();
    private static final UUID PARENT_PUBLIC_ID = UUID.randomUUID();
    private static final Long OWNER_ID = 1L;
    private static final Long COMMENTER_ID = 2L;
    private static final Long PARENT_AUTHOR_ID = 3L;

    private Planner planner;
    private User owner;
    private User commenter;
    private User parentAuthor;

    @BeforeEach
    void setUp() {
        service = new CommentCommandService(
                commentRepository,
                new CommentQueryService(commentRepository, commentVoteRepository, userService,
                        new PlannerAccessGuard(userService, plannerRepository),
                        new CommentAccessValidator()),
                userService,
                notificationDispatchService,
                eventPublisher,
                new PlannerAccessGuard(userService, plannerRepository),
                new PlannerStatsService(plannerStatsRepository),
                new CommentAccessValidator(), new CommentAuthorshipValidator(),
                new CommentStateValidator());

        owner = User.builder()
                .id(OWNER_ID)
                .email("owner@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("owner-123")
                .usernameEpithet("OWNER")
                .usernameSuffix("own01")
                .role(UserRole.NORMAL)
                .build();

        commenter = User.builder()
                .id(COMMENTER_ID)
                .email("commenter@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("commenter-123")
                .usernameEpithet("COMMENTER")
                .usernameSuffix("com01")
                .role(UserRole.NORMAL)
                .build();

        parentAuthor = User.builder()
                .id(PARENT_AUTHOR_ID)
                .email("parent@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("parent-123")
                .usernameEpithet("PARENT")
                .usernameSuffix("par01")
                .role(UserRole.NORMAL)
                .build();

        planner = TestDataFactory.planner(owner)
                .id(PLANNER_ID)
                .title("Test Planner")
                .category("5F")
                .status(PlannerStatus.DRAFT)
                .content("{}")
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .contentVersion(6)
                .published(true)
                .build();
        planner.setOwnerNotificationsEnabled(true);
    }

    @Nested
    @DisplayName("Top-level comment notification tests")
    class TopLevelCommentNotificationTests {

        @Test
        @DisplayName("Should send notification when owner notifications enabled")
        void createTopLevelComment_WhenNotificationEnabled_SendsNotification() {
            // Arrange
            planner.setOwnerNotificationsEnabled(true);
            UUID savedPublicId = UUID.randomUUID();
            when(userService.findById(COMMENTER_ID))
.thenReturn(commenter);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(inv -> {
                        PlannerComment c = inv.getArgument(0);
                        c.setId(1L);
                        c.setPublicId(savedPublicId);
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            CreateCommentRequest request = new CreateCommentRequest("Test comment", null);

            // Act
            UUID deviceId = UUID.randomUUID();
            service.createComment(PLANNER_ID, COMMENTER_ID, deviceId, request);

            // Assert - verify notification sent with correct params
            verify(notificationDispatchService).notifyCommentReceived(
                    eq(1L), eq(savedPublicId), eq(PLANNER_ID), eq("Test Planner"),
                    eq("Test comment"), eq(OWNER_ID), eq(COMMENTER_ID));
        }

        @Test
        @DisplayName("Should NOT send notification when owner notifications disabled")
        void createTopLevelComment_WhenNotificationDisabled_NoNotification() {
            // Arrange
            planner.setOwnerNotificationsEnabled(false);
            when(userService.findById(COMMENTER_ID))
.thenReturn(commenter);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(inv -> {
                        PlannerComment c = inv.getArgument(0);
                        c.setId(1L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            CreateCommentRequest request = new CreateCommentRequest("Test comment", null);

            // Act
            UUID deviceId = UUID.randomUUID();
            service.createComment(PLANNER_ID, COMMENTER_ID, deviceId, request);

            // Assert
            verify(notificationDispatchService, never()).notifyCommentReceived(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should NOT send notification when owner comments on own planner")
        void createTopLevelComment_WhenSelfComment_NoNotification() {
            // Arrange
            planner.setOwnerNotificationsEnabled(true);
            when(userService.findById(OWNER_ID))
.thenReturn(owner);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(inv -> {
                        PlannerComment c = inv.getArgument(0);
                        c.setId(1L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            CreateCommentRequest request = new CreateCommentRequest("Self comment", null);

            // Act
            UUID deviceId = UUID.randomUUID();
            service.createComment(PLANNER_ID, OWNER_ID, deviceId, request);

            // Assert - No notification for self-comment
            verify(notificationDispatchService, never()).notifyCommentReceived(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Reply comment notification tests")
    class ReplyCommentNotificationTests {

        private PlannerComment parentComment;

        @BeforeEach
        void setUp() {
            parentComment = new PlannerComment(PLANNER_ID, PARENT_AUTHOR_ID, "Parent content", null, 0);
            parentComment.setId(50L);
            parentComment.setCreatedAt(Instant.now());
            parentComment.setAuthorNotificationsEnabled(true);
        }

        @Test
        @DisplayName("Should send reply notification when author notifications enabled")
        void createReply_WhenAuthorNotificationEnabled_SendsNotification() {
            // Arrange
            parentComment.setAuthorNotificationsEnabled(true);
            UUID savedPublicId = UUID.randomUUID();
            when(userService.findById(COMMENTER_ID))
.thenReturn(commenter);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parentComment));
            when(commentRepository.findById(50L)).thenReturn(Optional.of(parentComment));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(inv -> {
                        PlannerComment c = inv.getArgument(0);
                        c.setId(101L);
                        c.setPublicId(savedPublicId);
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            CreateCommentRequest request = new CreateCommentRequest("Reply content", PARENT_PUBLIC_ID);

            // Act
            UUID deviceId = UUID.randomUUID();
            service.createComment(PLANNER_ID, COMMENTER_ID, deviceId, request);

            // Assert - verify notification sent with correct params
            verify(notificationDispatchService).notifyReplyReceived(
                    eq(101L), eq(savedPublicId), eq(PLANNER_ID), eq("Test Planner"),
                    eq("Reply content"), eq(PARENT_AUTHOR_ID), eq(COMMENTER_ID));
        }

        @Test
        @DisplayName("Should NOT send reply notification when author notifications disabled")
        void createReply_WhenAuthorNotificationDisabled_NoNotification() {
            // Arrange
            parentComment.setAuthorNotificationsEnabled(false);
            when(userService.findById(COMMENTER_ID))
.thenReturn(commenter);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parentComment));
            when(commentRepository.findById(50L)).thenReturn(Optional.of(parentComment));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(inv -> {
                        PlannerComment c = inv.getArgument(0);
                        c.setId(101L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            CreateCommentRequest request = new CreateCommentRequest("Reply content", PARENT_PUBLIC_ID);

            // Act
            UUID deviceId = UUID.randomUUID();
            service.createComment(PLANNER_ID, COMMENTER_ID, deviceId, request);

            // Assert
            verify(notificationDispatchService, never()).notifyReplyReceived(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should NOT send notification when replying to own comment")
        void createReply_WhenSelfReply_NoNotification() {
            // Arrange
            parentComment.setUserId(COMMENTER_ID); // Same user is replying to their own comment
            parentComment.setAuthorNotificationsEnabled(true);
            when(userService.findById(COMMENTER_ID))
.thenReturn(commenter);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parentComment));
            when(commentRepository.findById(50L)).thenReturn(Optional.of(parentComment));
            when(commentRepository.insert(any(PlannerComment.class)))
                    .thenAnswer(inv -> {
                        PlannerComment c = inv.getArgument(0);
                        c.setId(101L);
                        c.setPublicId(UUID.randomUUID());
                        c.setCreatedAt(Instant.now());
                        return c;
                    });

            CreateCommentRequest request = new CreateCommentRequest("Self reply", PARENT_PUBLIC_ID);

            // Act
            UUID deviceId = UUID.randomUUID();
            service.createComment(PLANNER_ID, COMMENTER_ID, deviceId, request);

            // Assert - No notification for self-reply
            verify(notificationDispatchService, never()).notifyReplyReceived(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Comment SSE payload")
    class CommentSsePayloadTests {

        @Test
        void createComment_WhenTopLevel_BroadcastsPayloadNamingNoParent() {
            when(userService.findById(COMMENTER_ID)).thenReturn(commenter);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID)).thenReturn(Optional.of(planner));
            when(commentRepository.insert(any(PlannerComment.class))).thenAnswer(savedComment(1L));

            service.createComment(PLANNER_ID, COMMENTER_ID, UUID.randomUUID(),
                    new CreateCommentRequest("Top level", null));

            assertThat(broadcastPayload().parentCommentId()).isNull();
        }

        @Test
        void createComment_WhenReply_BroadcastsPayloadNamingTheParent() {
            PlannerComment parent = new PlannerComment(PLANNER_ID, PARENT_AUTHOR_ID, "Parent", null, 0);
            parent.setId(50L);
            parent.setPublicId(PARENT_PUBLIC_ID);
            parent.setCreatedAt(Instant.now());
            parent.setAuthorNotificationsEnabled(false);

            when(userService.findById(COMMENTER_ID)).thenReturn(commenter);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID)).thenReturn(Optional.of(planner));
            when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parent));
            when(commentRepository.findById(50L)).thenReturn(Optional.of(parent));
            when(commentRepository.insert(any(PlannerComment.class))).thenAnswer(savedComment(101L));

            service.createComment(PLANNER_ID, COMMENTER_ID, UUID.randomUUID(),
                    new CreateCommentRequest("Reply", PARENT_PUBLIC_ID));

            assertThat(broadcastPayload().parentCommentId()).isEqualTo(PARENT_PUBLIC_ID);
        }

        @Test
        void createReply_WhenPosted_BroadcastsPayloadNamingTheParent() {
            PlannerComment parent = new PlannerComment(PLANNER_ID, PARENT_AUTHOR_ID, "Parent", null, 0);
            parent.setId(50L);
            parent.setPublicId(PARENT_PUBLIC_ID);
            parent.setCreatedAt(Instant.now());
            parent.setAuthorNotificationsEnabled(false);

            when(userService.findById(COMMENTER_ID)).thenReturn(commenter);
            when(plannerRepository.findPublishedAggregate(PLANNER_ID)).thenReturn(Optional.of(planner));
            when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parent));
            when(commentRepository.findById(50L)).thenReturn(Optional.of(parent));
            when(commentRepository.insert(any(PlannerComment.class))).thenAnswer(savedComment(101L));

            service.createReply(PARENT_PUBLIC_ID, COMMENTER_ID, UUID.randomUUID(), "Reply");

            assertThat(broadcastPayload().parentCommentId()).isEqualTo(PARENT_PUBLIC_ID);
        }

        private Answer<PlannerComment> savedComment(Long id) {
            return inv -> {
                PlannerComment c = inv.getArgument(0);
                c.setId(id);
                c.setPublicId(UUID.randomUUID());
                c.setCreatedAt(Instant.now());
                return c;
            };
        }

        private CommentTreeNode broadcastPayload() {
            ArgumentCaptor<CommentCreatedEvent> raised =
                    ArgumentCaptor.forClass(CommentCreatedEvent.class);
            verify(eventPublisher).publishEvent(raised.capture());

            CommentCreatedEvent event = raised.getValue();
            assertThat(event.plannerId()).isEqualTo(PLANNER_ID);
            assertThat(event.authorUserId()).isEqualTo(COMMENTER_ID);
            return event.payload();
        }
    }
}
