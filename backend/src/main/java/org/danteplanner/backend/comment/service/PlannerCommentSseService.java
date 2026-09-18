package org.danteplanner.backend.comment.service;

import org.danteplanner.backend.shared.sse.AbstractSseService;
import org.danteplanner.backend.shared.sse.SseCapacityExceededException;
import org.danteplanner.backend.shared.sse.SseConstants;
import org.danteplanner.backend.shared.sse.SseHeartbeatWorkerConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE service for planner comment notifications.
 */
@Service
@Slf4j
public class PlannerCommentSseService extends AbstractSseService<UUID> {

    private static final long HEARTBEAT_INTERVAL_MS = SseConstants.COMMENT_STREAM_HEARTBEAT_INTERVAL_MS;
    private static final long HEARTBEAT_INITIAL_DELAY_MS = 5_000L;
    private static final long CLEANUP_INITIAL_DELAY_MS = 30_000L;
    private static final int MAX_CONNECTIONS_PER_PLANNER = 500;

    private final ObjectMapper objectMapper;
    private final PlannerAccessGuard plannerAccessGuard;

    public PlannerCommentSseService(
            ObjectMapper objectMapper,
            PlannerAccessGuard plannerAccessGuard,
            @Qualifier(SseHeartbeatWorkerConfig.SSE_HEARTBEAT_WORKER) TaskExecutor heartbeatWorker) {
        super(heartbeatWorker);
        this.objectMapper = objectMapper;
        this.plannerAccessGuard = plannerAccessGuard;
    }

    public SseEmitter subscribe(UUID plannerId, UUID deviceId, Long userId) throws IOException {
        plannerAccessGuard.checkPublished(plannerId);
        SseEmitter emitter = register(plannerId, deviceId, userId);
        log.debug("Comment SSE subscribed: planner={}, device={}", plannerId, deviceId);
        return emitter;
    }

    private int sendToSubscribers(UUID plannerId, CopyOnWriteArrayList<EmitterEntry> subscribers,
                                  String eventName, String jsonData, Long excludeUserId) {
        int sent = 0;
        for (EmitterEntry entry : subscribers) {
            if (excludeUserId != null && excludeUserId.equals(entry.userId())) {
                continue;
            }

            try {
                entry.emitter().send(SseEmitter.event().name(eventName).data(jsonData));
                sent++;
            } catch (IOException | IllegalStateException e) {
                log.debug("Failed to send {} to planner {} device {}, removing", eventName, plannerId, entry.deviceId());
                removeConnection(plannerId, entry.deviceId());
            }
        }

        return sent;
    }

    public void broadcast(UUID plannerId, String eventType, Object payload, Long excludeUserId) {
        var subscribers = emitters.get(plannerId);
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for planner {} comment event {}", plannerId, eventType);
            return;
        }

        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize comment event {} for planner {}", eventType, plannerId, e);
            return;
        }

        sendToSubscribers(plannerId, subscribers, eventType, jsonData, excludeUserId);
    }

    public int getSubscriberCount(UUID plannerId) {
        return connectionCount(plannerId);
    }

    public int getTotalConnectionCount() {
        return emitters.values().stream()
                .mapToInt(CopyOnWriteArrayList::size)
                .sum();
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = HEARTBEAT_INITIAL_DELAY_MS)
    public void sweepSseHeartbeats() {
        sweepHeartbeatConnections();
    }

    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS, initialDelay = CLEANUP_INITIAL_DELAY_MS)
    public void cleanupZombieConnections() {
        int removed = cleanupConnections();
        if (removed > 0) {
            log.debug("Comment SSE cleanup removed {} zombie connections", removed);
        }
    }

    @Override
    protected void beforeRegister(UUID plannerId, CopyOnWriteArrayList<EmitterEntry> connections) {
        if (connections.size() >= MAX_CONNECTIONS_PER_PLANNER) {
            log.warn("Comment SSE: rejected a subscriber for planner {} (max {} reached)",
                    plannerId, MAX_CONNECTIONS_PER_PLANNER);
            throw new SseCapacityExceededException(plannerId, MAX_CONNECTIONS_PER_PLANNER);
        }
    }

    @Override
    protected void onUnsubscribed(UUID plannerId, UUID deviceId) {
        log.debug("Comment SSE unsubscribed: planner={}, device={}", plannerId, deviceId);
    }

    @Override
    protected void onHeartbeatFailure(UUID plannerId, UUID deviceId) {
        log.debug("Heartbeat failed for planner {} device {}, removing", plannerId, deviceId);
    }
}
