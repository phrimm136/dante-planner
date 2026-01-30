package org.danteplanner.backend.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.dto.moderation.BanRequest;
import org.danteplanner.backend.dto.moderation.TimeoutRequest;
import org.danteplanner.backend.dto.moderation.TimeoutResponse;
import org.danteplanner.backend.entity.Planner;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.service.ModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for moderation operations.
 *
 * <p>All endpoints require MODERATOR or higher role (enforced by SecurityConfig).
 * Provides timeout and content moderation capabilities.</p>
 */
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@Slf4j
public class ModerationController {

    private final ModerationService moderationService;
    private final org.danteplanner.backend.config.RateLimitConfig rateLimitConfig;

    /**
     * Timeout a user for a specified duration.
     *
     * <p>Timed-out users cannot create, edit, or publish planners.
     * Administrators cannot be timed out.</p>
     *
     * @param actorId  the moderator/admin user ID (from token)
     * @param usernameSuffix the username suffix of the user to timeout
     * @param request  the timeout request containing duration
     * @return the timeout information
     */
    @PostMapping("/user/{usernameSuffix}/timeout")
    public ResponseEntity<TimeoutResponse> timeoutUser(
            @AuthenticationPrincipal Long actorId,
            @PathVariable String usernameSuffix,
            @Valid @RequestBody TimeoutRequest request) {

        rateLimitConfig.checkModerationLimit(actorId);

        log.info("Moderator {} timing out user with suffix {} for {} minutes with reason: {}",
                actorId, usernameSuffix, request.getDurationMinutes(), request.getReason());

        User user = moderationService.timeoutUserBySuffix(actorId, usernameSuffix, request.getDurationMinutes(), request.getReason());
        return ResponseEntity.ok(TimeoutResponse.fromUser(user, "User timed out successfully"));
    }

    /**
     * Remove timeout from a user.
     *
     * <p>Allows the user to resume normal operations immediately.</p>
     *
     * @param actorId  the moderator/admin user ID (from token)
     * @param usernameSuffix the username suffix of the user to remove timeout from
     * @param request  reason for clearing timeout (required for audit trail)
     * @return the updated timeout information
     */
    @PostMapping("/user/{usernameSuffix}/clear-timeout")
    public ResponseEntity<TimeoutResponse> removeTimeout(
            @AuthenticationPrincipal Long actorId,
            @PathVariable String usernameSuffix,
            @Valid @RequestBody BanRequest request) {

        rateLimitConfig.checkModerationLimit(actorId);

        log.info("Moderator {} removing timeout from user with suffix {} with reason: {}", actorId, usernameSuffix, request.getReason());

        User user = moderationService.removeTimeoutBySuffix(actorId, usernameSuffix, request.getReason());
        return ResponseEntity.ok(TimeoutResponse.fromUser(user, "Timeout removed successfully"));
    }

    /**
     * Unpublish a planner.
     *
     * <p>Sets the planner's published status to false. The owner
     * can republish it later if they wish.</p>
     *
     * @param actorId   the moderator/admin user ID (from token)
     * @param plannerId the planner to unpublish
     * @return success message with planner status
     */
    @PutMapping("/planner/{plannerId}/unpublish")
    public ResponseEntity<Map<String, Object>> unpublishPlanner(
            @AuthenticationPrincipal Long actorId,
            @PathVariable UUID plannerId) {

        log.info("Moderator {} unpublishing planner {}", actorId, plannerId);

        Planner planner = moderationService.unpublishPlanner(actorId, plannerId);
        return ResponseEntity.ok(Map.of(
                "plannerId", planner.getId(),
                "published", planner.getPublished(),
                "message", "Planner unpublished successfully"
        ));
    }

    /**
     * Ban a user permanently.
     *
     * <p>Banned users cannot create, edit, publish planners, or submit comments.
     * Only admins can ban users. Administrators cannot be banned.</p>
     *
     * @param actorId  the admin user ID (from token)
     * @param usernameSuffix the username suffix of the user to ban
     * @param request  the ban request containing optional reason
     * @return success message
     */
    @PostMapping("/user/{usernameSuffix}/ban")
    public ResponseEntity<Map<String, Object>> banUser(
            @AuthenticationPrincipal Long actorId,
            @PathVariable String usernameSuffix,
            @Valid @RequestBody BanRequest request) {

        rateLimitConfig.checkModerationLimit(actorId);

        log.info("Admin {} banning user with suffix {} with reason: {}", actorId, usernameSuffix, request.getReason());

        User user = moderationService.banUserBySuffix(actorId, usernameSuffix, request.getReason());
        return ResponseEntity.ok(Map.of(
                "banned", user.isBanned(),
                "message", "User banned successfully"
        ));
    }

    /**
     * Unban a user.
     *
     * <p>Removes permanent ban, allowing the user to resume normal operations.</p>
     *
     * @param actorId  the admin user ID (from token)
     * @param usernameSuffix the username suffix of the user to unban
     * @param request  reason for unbanning (required for audit trail)
     * @return success message
     */
    @PostMapping("/user/{usernameSuffix}/unban")
    public ResponseEntity<Map<String, Object>> unbanUser(
            @AuthenticationPrincipal Long actorId,
            @PathVariable String usernameSuffix,
            @Valid @RequestBody BanRequest request) {

        rateLimitConfig.checkModerationLimit(actorId);

        log.info("Admin {} unbanning user with suffix {} with reason: {}", actorId, usernameSuffix, request.getReason());

        User user = moderationService.unbanUserBySuffix(actorId, usernameSuffix, request.getReason());
        return ResponseEntity.ok(Map.of(
                "banned", user.isBanned(),
                "message", "User unbanned successfully"
        ));
    }

    /**
     * Get all users for moderation dashboard.
     *
     * <p>Returns paginated list of all users with their restriction status.</p>
     *
     * @return list of users
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = moderationService.getAllUsers();
        List<Map<String, Object>> responses = users.stream()
                .map(user -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("usernameEpithet", user.getUsernameEpithet());
                    map.put("usernameSuffix", user.getUsernameSuffix());
                    map.put("role", user.getRole().name());
                    map.put("isBanned", user.isBanned());
                    map.put("bannedAt", user.getBannedAt() != null ? user.getBannedAt().toString() : "");
                    map.put("isTimedOut", user.isTimedOut());
                    map.put("timeoutUntil", user.getTimeoutUntil() != null ? user.getTimeoutUntil().toString() : "");
                    return map;
                })
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Get all currently timed-out users.
     *
     * <p>Returns a list of users with active timeouts.
     * Useful for moderation dashboards.</p>
     *
     * @return list of timed-out users
     */
    @GetMapping("/users/timed-out")
    public ResponseEntity<List<TimeoutResponse>> getTimedOutUsers() {
        List<User> timedOutUsers = moderationService.getTimedOutUsers();
        List<TimeoutResponse> responses = timedOutUsers.stream()
                .map(user -> TimeoutResponse.fromUser(user, null))
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Get moderation action history.
     *
     * <p>Returns recent moderation actions for audit trail.</p>
     *
     * @return list of moderation actions
     */
    @GetMapping("/actions")
    public ResponseEntity<List<org.danteplanner.backend.dto.moderation.ModerationActionDto>> getModerationActions() {
        List<org.danteplanner.backend.dto.moderation.ModerationActionDto> actions = moderationService.getModerationActionsWithActors();
        return ResponseEntity.ok(actions);
    }

    /**
     * Take down a planner (moderator deletion).
     *
     * <p>Removes planner from public view but allows owner to sync their local copy.
     * Planner cannot be re-published once taken down.</p>
     *
     * @param actorId   the moderator/admin user ID (from token)
     * @param plannerId the planner to take down
     * @param request   reason for takedown (required for audit trail)
     * @return success message
     */
    @PostMapping("/planner/{plannerId}/takedown")
    public ResponseEntity<Map<String, Object>> takedownPlanner(
            @AuthenticationPrincipal Long actorId,
            @PathVariable UUID plannerId,
            @Valid @RequestBody BanRequest request) {

        rateLimitConfig.checkModerationLimit(actorId);

        log.info("Moderator {} taking down planner {} with reason: {}", actorId, plannerId, request.getReason());

        moderationService.deletePlanner(actorId, plannerId, request.getReason());
        return ResponseEntity.ok(Map.of(
                "plannerId", plannerId,
                "message", "Planner taken down successfully"
        ));
    }

    /**
     * Delete a comment as a moderator.
     *
     * <p>Soft-deletes the comment, preserving thread structure.
     * Content is cleared but placeholder remains.</p>
     *
     * @param actorId   the moderator/admin user ID (from token)
     * @param commentPublicId the comment public ID (UUID) to delete
     * @param request   reason for deletion (required for audit trail)
     * @return 204 No Content on success
     */
    @PostMapping("/comments/{commentPublicId}/delete")
    public ResponseEntity<Void> deleteComment(
            @AuthenticationPrincipal Long actorId,
            @PathVariable UUID commentPublicId,
            @Valid @RequestBody BanRequest request) {

        rateLimitConfig.checkModerationLimit(actorId);

        log.info("Moderator {} deleting comment {} with reason: {}", actorId, commentPublicId, request.getReason());
        moderationService.deleteCommentByPublicId(actorId, commentPublicId, request.getReason());
        return ResponseEntity.noContent().build();
    }
}
