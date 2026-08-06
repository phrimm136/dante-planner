package org.danteplanner.backend.comment.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;
import org.danteplanner.backend.comment.dto.CommentTreeNode;
import org.danteplanner.backend.comment.dto.CommentVoteResponse;
import org.danteplanner.backend.comment.dto.CreateCommentRequest;
import org.danteplanner.backend.comment.dto.CreateCommentResponse;
import org.danteplanner.backend.shared.dto.ToggleNotificationRequest;
import org.danteplanner.backend.comment.dto.ToggleNotificationResponse;
import org.danteplanner.backend.comment.dto.UpdateCommentRequest;
import org.danteplanner.backend.comment.dto.UpdateCommentResponse;
import org.danteplanner.backend.comment.service.CommentCommandService;
import org.danteplanner.backend.comment.service.CommentEngagementService;
import org.danteplanner.backend.comment.service.CommentQueryService;
import org.danteplanner.backend.moderation.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.dto.CommentReportResponse;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for comment operations.
 *
 * <p>Provides CRUD operations for planner comments and upvote toggling.
 * Comments on published planners are publicly readable, but writing requires authentication.</p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@RateLimited(RateLimitPolicy.COMMENT)
public class CommentController {

    private final CommentQueryService commentQueryService;
    private final CommentCommandService commentCommandService;
    private final CommentEngagementService commentEngagementService;
    private final RateLimitService rateLimitService;

    /**
     * Get all comments for a planner.
     *
     * <p>Returns a flat list of comments ordered by creation time.
     * Frontend builds tree structure from parentCommentId.
     * Includes deleted comments to preserve thread structure.</p>
     *
     * @param userId    the current user ID (null if unauthenticated)
     * @param plannerId the planner ID
     * @return list of comments with vote status
     */
    @RateLimitExempt
    @GetMapping("/planner/{plannerId}/comments")
    public ResponseEntity<List<CommentTreeNode>> getComments(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID plannerId) {

        List<CommentTreeNode> comments = commentQueryService.getCommentTree(plannerId, userId);
        return ResponseEntity.ok(comments);
    }

    /**
     * Create a new comment on a planner.
     *
     * <p>Supports both top-level comments and threaded replies.
     * Max depth is 5; replies at max depth become siblings.
     * Rate limited to prevent spam.</p>
     *
     * @param userId    the authenticated user ID
     * @param deviceId  the device identifier (for SSE broadcast exclusion)
     * @param plannerId the planner ID
     * @param request   the comment content and optional parent ID
     * @return the created comment id and timestamp
     */
    @PostMapping("/planner/{plannerId}/comments")
    public ResponseEntity<CreateCommentResponse> createComment(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID plannerId,
            @Valid @RequestBody CreateCommentRequest request) {

        rateLimitService.check(RateLimitPolicy.COMMENT, userId);

        log.info("User {} creating comment on planner {}", userId, plannerId);
        CreateCommentResponse response = commentCommandService.createComment(plannerId, userId, deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Create a reply to an existing comment.
     *
     * <p>Creates a threaded reply under the specified parent comment.
     * Max depth is 5; replies at max depth become siblings.
     * Rate limited to prevent spam.</p>
     *
     * @param userId          the authenticated user ID
     * @param deviceId        the device identifier (for SSE broadcast exclusion)
     * @param parentCommentId the parent comment's public UUID
     * @param request         the reply content
     * @return the created reply id and timestamp
     */
    @PostMapping("/comments/{parentCommentId}/replies")
    public ResponseEntity<CreateCommentResponse> createReply(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID parentCommentId,
            @Valid @RequestBody CreateCommentRequest request) {

        rateLimitService.check(RateLimitPolicy.COMMENT, userId);

        log.info("User {} creating reply to comment {}", userId, parentCommentId);
        CreateCommentResponse response = commentCommandService.createReply(parentCommentId, userId, deviceId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Edit a comment.
     *
     * <p>Only the comment author can edit. Sets editedAt timestamp.
     * Rate limited to prevent spam.</p>
     *
     * @param userId    the authenticated user ID (must be author)
     * @param commentId the comment ID
     * @param request   the new content
     * @return the edit timestamp
     */
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<UpdateCommentResponse> updateComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {

        rateLimitService.check(RateLimitPolicy.COMMENT, userId);

        log.info("User {} editing comment {}", userId, commentId);
        UpdateCommentResponse response = commentCommandService.updateComment(commentId, userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a comment (soft-delete).
     *
     * <p>Only the comment author can delete their own comment.
     * Content is cleared but comment structure is preserved for thread integrity.</p>
     *
     * @param userId    the authenticated user ID (must be author)
     * @param commentId the comment ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId) {

        rateLimitService.check(RateLimitPolicy.COMMENT, userId);

        log.info("User {} deleting comment {}", userId, commentId);
        commentCommandService.deleteComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Toggle upvote on a comment.
     *
     * <p>Clicking upvote on an already-upvoted comment removes the vote.
     * Uses atomic counter operations to prevent race conditions.
     * Rate limited to prevent vote manipulation.</p>
     *
     * @param userId    the authenticated user ID
     * @param commentId the comment ID
     * @return updated vote count and user's vote status
     */
    @PostMapping("/comments/{commentId}/upvote")
    public ResponseEntity<CommentVoteResponse> toggleUpvote(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId) {

        rateLimitService.check(RateLimitPolicy.COMMENT, userId);

        CommentVoteResponse response = commentEngagementService.toggleUpvote(commentId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Submit a report for a comment.
     *
     * <p>Requires authentication. One report per user per comment: a repeat report is
     * 409 Conflict. Reporting a deleted comment is 403 Forbidden.</p>
     *
     * @param userId    the authenticated user ID
     * @param commentId the comment public UUID
     * @param request   the report request with reason
     * @return the report timestamp
     */
    @RateLimited(RateLimitPolicy.REPORT)
    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<CommentReportResponse> reportComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentReportRequest request) {

        rateLimitService.check(RateLimitPolicy.REPORT, userId);

        log.info("User {} reporting comment {}", userId, commentId);
        CommentReportResponse response = commentEngagementService.reportComment(commentId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Toggle notification setting for a comment.
     *
     * <p>Only the comment author can toggle their notification setting.
     * When disabled, the author will not receive notifications for replies to this comment.</p>
     *
     * @param userId    the authenticated user ID (must be author)
     * @param commentId the comment public UUID
     * @param request   the toggle request with enabled flag
     * @return the toggle result
     */
    @PatchMapping("/comments/{commentId}/notifications")
    public ResponseEntity<ToggleNotificationResponse> toggleNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId,
            @Valid @RequestBody ToggleNotificationRequest request) {

        rateLimitService.check(RateLimitPolicy.COMMENT, userId);

        log.info("User {} toggling notifications for comment {}", userId, commentId);
        ToggleNotificationResponse response = commentEngagementService.toggleNotification(commentId, userId, request.enabled());
        return ResponseEntity.ok(response);
    }
}
