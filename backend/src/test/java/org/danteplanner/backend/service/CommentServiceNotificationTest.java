package org.danteplanner.backend.service;

import org.danteplanner.backend.dto.comment.CreateCommentRequest;
import org.danteplanner.backend.entity.*;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommentService notification flag behavior.
 * Tests that notifications respect ownerNotificationsEnabled and authorNotificationsEnabled settings.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceNotificationTest {

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

    private CommentService service;

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
        service = new CommentService(
                commentRepository,
                commentVoteRepository,
                plannerRepository,
                userRepository,
                notificationService,
                plannerCommentSseService
        );

        owner = User.builder()
                .id(OWNER_ID)
                .email("owner@example.com")
                .provider("google")
                .providerId("owner-123")
                .usernameEpithet("OWNER")
                .usernameSuffix("own01")
                .role(UserRole.NORMAL)
                .build();

        commenter = User.builder()
                .id(COMMENTER_ID)
                .email("commenter@example.com")
                .provider("google")
                .providerId("commenter-123")
                .usernameEpithet("COMMENTER")
                .usernameSuffix("com01")
                .role(UserRole.NORMAL)
                .build();

        parentAuthor = User.builder()
                .id(PARENT_AUTHOR_ID)
                .email("parent@example.com")
                .provider("google")
                .providerId("parent-123")
                .usernameEpithet("PARENT")
                .usernameSuffix("par01")
                .role(UserRole.NORMAL)
                .build();

        planner = Planner.builder()
                .id(PLANNER_ID)
                .user(owner)
                .title("Test Planner")
                .category("5F")
                .status("draft")
                .content("{}")
                .plannerType(PlannerType.MIRROR_DUNGEON)
                .contentVersion(6)
                .published(true)
                .ownerNotificationsEnabled(true)
                .build();
    }

    @Nested
    @DisplayName("Top-level comment notification tests")
    class TopLevelCommentNotificationTests {

        @Test
        @DisplayName("Should send notification when owner notifications enabled")
        void createTopLevelComment_NotificationEnabled_SendsNotification() {
            // Arrange
            planner.setOwnerNotificationsEnabled(true);
            UUID savedPublicId = UUID.randomUUID();
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.save(any(PlannerComment.class)))
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
            verify(notificationService).notifyCommentReceived(
                    eq(1L), eq(savedPublicId), eq(PLANNER_ID), eq("Test Planner"),
                    eq("Test comment"), eq(OWNER_ID), eq(COMMENTER_ID));
        }

        @Test
        @DisplayName("Should NOT send notification when owner notifications disabled")
        void createTopLevelComment_NotificationDisabled_NoNotification() {
            // Arrange
            planner.setOwnerNotificationsEnabled(false);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.save(any(PlannerComment.class)))
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
            verify(notificationService, never()).notifyCommentReceived(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should NOT send notification when owner comments on own planner")
        void createTopLevelComment_SelfComment_NoNotification() {
            // Arrange
            planner.setOwnerNotificationsEnabled(true);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.save(any(PlannerComment.class)))
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
            verify(notificationService, never()).notifyCommentReceived(any(), any(), any(), any(), any(), any(), any());
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
        void createReply_AuthorNotificationEnabled_SendsNotification() {
            // Arrange
            parentComment.setAuthorNotificationsEnabled(true);
            UUID savedPublicId = UUID.randomUUID();
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parentComment));
            when(commentRepository.findById(50L)).thenReturn(Optional.of(parentComment));
            when(commentRepository.save(any(PlannerComment.class)))
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
            verify(notificationService).notifyReplyReceived(
                    eq(101L), eq(savedPublicId), eq(PLANNER_ID), eq("Test Planner"),
                    eq("Reply content"), eq(PARENT_AUTHOR_ID), eq(COMMENTER_ID));
        }

        @Test
        @DisplayName("Should NOT send reply notification when author notifications disabled")
        void createReply_AuthorNotificationDisabled_NoNotification() {
            // Arrange
            parentComment.setAuthorNotificationsEnabled(false);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parentComment));
            when(commentRepository.findById(50L)).thenReturn(Optional.of(parentComment));
            when(commentRepository.save(any(PlannerComment.class)))
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
            verify(notificationService, never()).notifyReplyReceived(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should NOT send notification when replying to own comment")
        void createReply_SelfReply_NoNotification() {
            // Arrange
            parentComment.setUserId(COMMENTER_ID); // Same user is replying to their own comment
            parentComment.setAuthorNotificationsEnabled(true);
            when(plannerRepository.findByIdAndPublishedTrueAndDeletedAtIsNull(PLANNER_ID))
                    .thenReturn(Optional.of(planner));
            when(commentRepository.findByPublicId(PARENT_PUBLIC_ID)).thenReturn(Optional.of(parentComment));
            when(commentRepository.findById(50L)).thenReturn(Optional.of(parentComment));
            when(commentRepository.save(any(PlannerComment.class)))
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
            verify(notificationService, never()).notifyReplyReceived(any(), any(), any(), any(), any(), any(), any());
        }
    }
}
