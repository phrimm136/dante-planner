package org.danteplanner.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Timeout a user for a specified duration.
     *
     * <p>Timed-out users cannot create, edit, or publish planners.
     * Administrators cannot be timed out.</p>
     *
     * @param actorId  the moderator/admin user ID (from token)
     * @param targetId the user to timeout
     * @param request  the timeout request containing duration
     * @return the timeout information
     */
    @PostMapping("/user/{targetId}/timeout")
    public ResponseEntity<TimeoutResponse> timeoutUser(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long targetId,
            @Valid @RequestBody TimeoutRequest request) {

        log.info("Moderator {} timing out user {} for {} minutes",
                actorId, targetId, request.getDurationMinutes());

        User user = moderationService.timeoutUser(actorId, targetId, request.getDurationMinutes());
        return ResponseEntity.ok(TimeoutResponse.fromUser(user, "User timed out successfully"));
    }

    /**
     * Remove timeout from a user.
     *
     * <p>Allows the user to resume normal operations immediately.</p>
     *
     * @param actorId  the moderator/admin user ID (from token)
     * @param targetId the user to remove timeout from
     * @return the updated timeout information
     */
    @DeleteMapping("/user/{targetId}/timeout")
    public ResponseEntity<TimeoutResponse> removeTimeout(
            @AuthenticationPrincipal Long actorId,
            @PathVariable Long targetId) {

        log.info("Moderator {} removing timeout from user {}", actorId, targetId);

        User user = moderationService.removeTimeout(actorId, targetId);
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
}
