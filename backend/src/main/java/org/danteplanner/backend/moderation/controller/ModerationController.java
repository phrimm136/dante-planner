package org.danteplanner.backend.moderation.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.dto.BanRequest;
import org.danteplanner.backend.moderation.dto.BanStatusResponse;
import org.danteplanner.backend.moderation.dto.ModeratedUserResponse;
import org.danteplanner.backend.moderation.dto.ModerationActionResponse;
import org.danteplanner.backend.moderation.dto.PlannerActionResponse;
import org.danteplanner.backend.moderation.dto.TimeoutRequest;
import org.danteplanner.backend.moderation.dto.TimeoutResponse;
import org.danteplanner.backend.moderation.dto.UnpublishPlannerResponse;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.moderation.service.CommentModerationService;
import org.danteplanner.backend.moderation.service.ModerationQueryService;
import org.danteplanner.backend.moderation.service.PlannerModerationService;
import org.danteplanner.backend.moderation.service.UserModerationService;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.shared.ratelimit.RateLimitExempt;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@Slf4j
@RateLimited(RateLimitPolicy.MODERATION)
public class ModerationController {

    private final UserModerationService userModerationService;
    private final PlannerModerationService plannerModerationService;
    private final CommentModerationService commentModerationService;
    private final ModerationQueryService moderationQueryService;

    @PostMapping("/user/{usernameSuffix}/timeout")
    public ResponseEntity<TimeoutResponse> timeoutUser(
            @AuthenticationPrincipal Long actorId,
            @PathVariable String usernameSuffix,
            @Valid @RequestBody TimeoutRequest request) {

        log.info("Moderator {} timing out user with suffix {} for {} minutes with reason: {}",
                actorId, usernameSuffix, request.durationMinutes(), request.reason());

        User user = userModerationService.timeoutUserBySuffix(actorId, usernameSuffix, request.durationMinutes(), request.reason());
        return ResponseEntity.ok(TimeoutResponse.fromUser(user, "User timed out successfully"));
    }

    @PostMapping("/user/{usernameSuffix}/clear-timeout")
    public ResponseEntity<TimeoutResponse> removeTimeout(
            @AuthenticationPrincipal Long actorId,
            @PathVariable String usernameSuffix,
            @Valid @RequestBody BanRequest request) {

        log.info("Moderator {} removing timeout from user with suffix {} with reason: {}", actorId, usernameSuffix, request.reason());

        User user = userModerationService.removeTimeoutBySuffix(actorId, usernameSuffix, request.reason());
        return ResponseEntity.ok(TimeoutResponse.fromUser(user, "Timeout removed successfully"));
    }

    @PutMapping("/planner/{plannerId}/unpublish")
    public ResponseEntity<UnpublishPlannerResponse> unpublishPlanner(
            @AuthenticationPrincipal Long actorId,
            @PathVariable UUID plannerId) {

        log.info("Moderator {} unpublishing planner {}", actorId, plannerId);

        Planner planner = plannerModerationService.unpublishPlanner(actorId, plannerId);
        return ResponseEntity.ok(new UnpublishPlannerResponse(
                planner.getId(), planner.isPublished(), "Planner unpublished successfully"));
    }

    @PostMapping("/user/{usernameSuffix}/ban")
    public ResponseEntity<BanStatusResponse> banUser(
            @AuthenticationPrincipal Long actorId,
            @PathVariable String usernameSuffix,
            @Valid @RequestBody BanRequest request) {

        log.info("Admin {} banning user with suffix {} with reason: {}", actorId, usernameSuffix, request.reason());

        User user = userModerationService.banUserBySuffix(actorId, usernameSuffix, request.reason());
        return ResponseEntity.ok(new BanStatusResponse(user.isBanned(), "User banned successfully"));
    }

    @PostMapping("/user/{usernameSuffix}/unban")
    public ResponseEntity<BanStatusResponse> unbanUser(
            @AuthenticationPrincipal Long actorId,
            @PathVariable String usernameSuffix,
            @Valid @RequestBody BanRequest request) {

        log.info("Admin {} unbanning user with suffix {} with reason: {}", actorId, usernameSuffix, request.reason());

        User user = userModerationService.unbanUserBySuffix(actorId, usernameSuffix, request.reason());
        return ResponseEntity.ok(new BanStatusResponse(user.isBanned(), "User unbanned successfully"));
    }

    @RateLimitExempt
    @GetMapping("/users")
    public ResponseEntity<List<ModeratedUserResponse>> getAllUsers() {
        List<ModeratedUserResponse> responses = moderationQueryService.getAllUsers().stream()
                .map(ModeratedUserResponse::fromUser)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @RateLimitExempt
    @GetMapping("/users/timed-out")
    public ResponseEntity<List<TimeoutResponse>> getTimedOutUsers() {
        List<User> timedOutUsers = moderationQueryService.getTimedOutUsers();
        List<TimeoutResponse> responses = timedOutUsers.stream()
                .map(TimeoutResponse::fromUser)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @RateLimitExempt
    @GetMapping("/actions")
    public ResponseEntity<List<ModerationActionResponse>> getModerationActions() {
        List<ModerationActionResponse> actions = moderationQueryService.getModerationActionsWithActors();
        return ResponseEntity.ok(actions);
    }

    @PostMapping("/planner/{plannerId}/takedown")
    public ResponseEntity<PlannerActionResponse> takedownPlanner(
            @AuthenticationPrincipal Long actorId,
            @PathVariable UUID plannerId,
            @Valid @RequestBody BanRequest request) {

        log.info("Moderator {} taking down planner {} with reason: {}", actorId, plannerId, request.reason());

        plannerModerationService.deletePlanner(actorId, plannerId, request.reason());
        return ResponseEntity.ok(
                new PlannerActionResponse(plannerId, "Planner taken down successfully"));
    }

    @PostMapping("/comments/{commentPublicId}/delete")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal Long actorId,
            @PathVariable UUID commentPublicId,
            @Valid @RequestBody BanRequest request) {

        log.info("Moderator {} deleting comment {} with reason: {}", actorId, commentPublicId, request.reason());
        commentModerationService.deleteCommentByPublicId(actorId, commentPublicId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
