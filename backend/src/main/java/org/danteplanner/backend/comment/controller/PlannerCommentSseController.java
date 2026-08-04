package org.danteplanner.backend.comment.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.config.SecurityProperties;
import org.danteplanner.backend.shared.service.RateLimitPolicy;
import org.danteplanner.backend.shared.service.RateLimitService;
import org.danteplanner.backend.shared.util.ClientIpResolver;
import org.danteplanner.backend.comment.service.PlannerCommentSseService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * REST controller for planner comment SSE subscriptions.
 *
 * <p>Provides a public SSE endpoint for real-time comment notifications.
 * Unlike {@link org.danteplanner.backend.shared.controller.SseController}, this endpoint does not require authentication,
 * allowing guests to receive comment updates.</p>
 *
 * <p>When a new comment is posted on a planner, all subscribers to that
 * planner's comment feed receive a {@code comment:added} event.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner")
@Slf4j
public class PlannerCommentSseController {

    private final PlannerCommentSseService plannerCommentSseService;
    private final RateLimitService rateLimitService;
    private final SecurityProperties securityProperties;

    /**
     * Subscribe to comment notifications for a specific planner.
     *
     * <p>Returns an SSE stream that sends {@code comment:added} events
     * when new comments are posted on this planner.</p>
     *
     * <p>No authentication required - guests can subscribe. The bucket is keyed by the resolved
     * client identifier rather than the device id: an absent device cookie is minted fresh per
     * request, so a caller that discards cookies would otherwise get an unlimited supply of
     * buckets and could evict a planner's real subscribers from the connection registry.</p>
     *
     * @param plannerId   the planner ID to subscribe to
     * @param deviceId    the device identifier (from HTTP-only cookie)
     * @param userId      the authenticated account, or null for a guest
     * @param httpRequest the HTTP request the rate-limit identity is resolved from
     * @return the SSE emitter
     * @throws org.danteplanner.backend.planner.exception.PlannerNotFoundException
     *         if no published planner carries the id
     */
    @GetMapping(value = "/{plannerId}/comments/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToComments(
            @PathVariable UUID plannerId,
            @DeviceId UUID deviceId,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest httpRequest) {

        String identifier = ClientIpResolver.resolveClientIdentifier(
                httpRequest, securityProperties, deviceId);
        rateLimitService.check(RateLimitPolicy.PLANNER_COMMENT_SSE, identifier);
        log.debug("Comment SSE subscription for planner {} device {}", plannerId, deviceId);
        return plannerCommentSseService.subscribe(plannerId, deviceId, userId);
    }
}
