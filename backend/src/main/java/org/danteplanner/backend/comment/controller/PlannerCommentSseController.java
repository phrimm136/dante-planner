package org.danteplanner.backend.comment.controller;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.shared.config.DeviceId;
import org.danteplanner.backend.shared.ratelimit.RateLimitPolicy;
import org.danteplanner.backend.comment.service.PlannerCommentSseService;
import org.danteplanner.backend.shared.ratelimit.RateLimited;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planner")
public class PlannerCommentSseController {

    private final PlannerCommentSseService plannerCommentSseService;

    @RateLimited(RateLimitPolicy.PLANNER_COMMENT_SSE)
    @GetMapping(value = "/{plannerId}/comments/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToComments(
            @PathVariable UUID plannerId,
            @DeviceId UUID deviceId,
            @AuthenticationPrincipal Long userId) throws IOException {

        return plannerCommentSseService.subscribe(plannerId, deviceId, userId);
    }
}
