package org.danteplanner.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.DeviceId;
import org.danteplanner.backend.config.RateLimitConfig;
import org.danteplanner.backend.service.PlannerCommentSseService;
import org.springframework.http.MediaType;
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
 * Unlike {@link SseController}, this endpoint does not require authentication,
 * allowing guests to receive comment updates.</p>
 *
 * <p>When a new comment is posted on a planner, all subscribers to that
 * planner's comment feed receive a {@code comment:added} event, except
 * for the device that posted the comment.</p>
 */
@RestController
@RequestMapping("/api/planner")
@Slf4j
public class PlannerCommentSseController {

    private final PlannerCommentSseService plannerCommentSseService;
    private final RateLimitConfig rateLimitConfig;

    public PlannerCommentSseController(
            PlannerCommentSseService plannerCommentSseService,
            RateLimitConfig rateLimitConfig) {
        this.plannerCommentSseService = plannerCommentSseService;
        this.rateLimitConfig = rateLimitConfig;
    }

    /**
     * Subscribe to comment notifications for a specific planner.
     *
     * <p>Returns an SSE stream that sends {@code comment:added} events
     * when new comments are posted on this planner.</p>
     *
     * <p>No authentication required - guests can subscribe.
     * Rate limited by device ID to prevent abuse.</p>
     *
     * @param plannerId the planner ID to subscribe to
     * @param deviceId  the device identifier (from HTTP-only cookie)
     * @return the SSE emitter
     */
    @GetMapping(value = "/{plannerId}/comments/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToComments(
            @PathVariable UUID plannerId,
            @DeviceId UUID deviceId) {

        rateLimitConfig.checkPlannerCommentSseLimit(deviceId);
        log.debug("Comment SSE subscription for planner {} device {}", plannerId, deviceId);
        return plannerCommentSseService.subscribe(plannerId, deviceId);
    }
}
