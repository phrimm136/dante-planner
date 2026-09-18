package org.danteplanner.backend.comment.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@RateLimited(RateLimitPolicy.COMMENT)
public class CommentController {

    private final CommentQueryService commentQueryService;
    private final CommentCommandService commentCommandService;
    private final CommentEngagementService commentEngagementService;

    @RateLimitExempt
    @GetMapping("/planner/{plannerId}/comments")
    public ResponseEntity<List<CommentTreeNode>> getComments(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID plannerId) {

        List<CommentTreeNode> comments = commentQueryService.getCommentTree(plannerId, userId);
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/planner/{plannerId}/comments")
    public ResponseEntity<CreateCommentResponse> createComment(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID plannerId,
            @Valid @RequestBody CreateCommentRequest request) {

        CreateCommentResponse response = commentCommandService.createComment(plannerId, userId, deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/comments/{parentCommentId}/replies")
    public ResponseEntity<CreateCommentResponse> createReply(
            @AuthenticationPrincipal Long userId,
            @DeviceId UUID deviceId,
            @PathVariable UUID parentCommentId,
            @Valid @RequestBody CreateCommentRequest request) {

        CreateCommentResponse response = commentCommandService.createReply(parentCommentId, userId, deviceId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<UpdateCommentResponse> updateComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {

        UpdateCommentResponse response = commentCommandService.updateComment(commentId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId) {

        commentCommandService.deleteComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/upvote")
    public ResponseEntity<CommentVoteResponse> toggleUpvote(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId) {

        CommentVoteResponse response = commentEngagementService.toggleUpvote(commentId, userId);
        return ResponseEntity.ok(response);
    }

    @RateLimited(RateLimitPolicy.REPORT)
    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<CommentReportResponse> reportComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentReportRequest request) {

        CommentReportResponse response = commentEngagementService.reportComment(commentId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/comments/{commentId}/notifications")
    public ResponseEntity<ToggleNotificationResponse> toggleNotification(
            @AuthenticationPrincipal Long userId,
            @PathVariable UUID commentId,
            @Valid @RequestBody ToggleNotificationRequest request) {

        ToggleNotificationResponse response = commentEngagementService.toggleNotification(commentId, userId, request.enabled());
        return ResponseEntity.ok(response);
    }
}
