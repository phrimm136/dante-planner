package org.danteplanner.backend.comment.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.shared.restriction.Restricted;
import org.danteplanner.backend.shared.restriction.RestrictionExempt;
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
import org.danteplanner.backend.moderation.api.dto.CommentReportRequest;
import org.danteplanner.backend.moderation.api.dto.CommentReportResponse;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            Viewer viewer,
            @PathVariable UUID plannerId) {

        return ResponseEntity.ok(commentQueryService.getCommentTree(plannerId, viewer).value());
    }

    @Restricted
    @PostMapping("/planner/{plannerId}/comments")
    public ResponseEntity<CreateCommentResponse> createComment(
            Viewer.Member member,
            @DeviceId UUID deviceId,
            @PathVariable UUID plannerId,
            @Valid @RequestBody CreateCommentRequest request) {

        CreateCommentResponse response = commentCommandService.createComment(plannerId, member.userId(), deviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Restricted
    @PostMapping("/comments/{parentCommentId}/replies")
    public ResponseEntity<CreateCommentResponse> createReply(
            Viewer.Member member,
            @DeviceId UUID deviceId,
            @PathVariable UUID parentCommentId,
            @Valid @RequestBody CreateCommentRequest request) {

        CreateCommentResponse response = commentCommandService.createReply(parentCommentId, member.userId(), deviceId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Restricted
    @PutMapping("/comments/{commentId}")
    public ResponseEntity<UpdateCommentResponse> updateComment(
            Viewer.Member member,
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request) {

        UpdateCommentResponse response = commentCommandService.updateComment(commentId, member.userId(), request);
        return ResponseEntity.ok(response);
    }

    @RestrictionExempt(reason = "own comment")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            Viewer.Member member,
            @PathVariable UUID commentId) {

        commentCommandService.deleteComment(commentId, member.userId());
        return ResponseEntity.noContent().build();
    }

    @Restricted
    @PostMapping("/comments/{commentId}/upvote")
    public ResponseEntity<CommentVoteResponse> toggleUpvote(
            Viewer.Member member,
            @PathVariable UUID commentId) {

        CommentVoteResponse response = commentEngagementService.toggleUpvote(commentId, member.userId());
        return ResponseEntity.ok(response);
    }

    @RateLimited(RateLimitPolicy.REPORT)
    @Restricted
    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<CommentReportResponse> reportComment(
            Viewer.Member member,
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentReportRequest request) {

        CommentReportResponse response = commentEngagementService.reportComment(commentId, member.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @RestrictionExempt(reason = "own setting")
    @PatchMapping("/comments/{commentId}/notifications")
    public ResponseEntity<ToggleNotificationResponse> toggleNotification(
            Viewer.Member member,
            @PathVariable UUID commentId,
            @Valid @RequestBody ToggleNotificationRequest request) {

        ToggleNotificationResponse response = commentEngagementService.toggleNotification(commentId, member.userId(), request.enabled());
        return ResponseEntity.ok(response);
    }
}
