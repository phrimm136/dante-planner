package org.danteplanner.backend.comment.service;

import org.danteplanner.backend.shared.sse.AbstractSseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.service.PlannerAccessGuard;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE service for planner comment notifications.
 *
 * <p>Unlike {@link org.danteplanner.backend.shared.sse.SseService} which is user-centric, this service is planner-centric.
 * Any device (authenticated or guest) can subscribe to a planner's comment feed
 * and receive notifications when new comments are posted.</p>
 *
 * <p>Key differences from SseService:
 * <ul>
 *   <li>Keyed by plannerId instead of userId</li>
 *   <li>No authentication required (guests can subscribe)</li>
 *   <li>No settings filtering (all subscribers receive all events)</li>
 *   <li>Author's device is excluded when broadcasting</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerCommentSseService extends AbstractSseService<UUID> {

    private static final long HEARTBEAT_INTERVAL_MS = 15_000L; // 15 seconds
    private static final long HEARTBEAT_INITIAL_DELAY_MS = 5_000L;
    private static final long CLEANUP_INITIAL_DELAY_MS = 30_000L;
    private static final int MAX_CONNECTIONS_PER_PLANNER = 500; // Prevent DoS

    private final ObjectMapper objectMapper;
    private final PlannerAccessGuard plannerAccessGuard;

    /**
     * Subscribe a device to receive comment notifications for a planner.
     *
     * @param plannerId the planner ID to subscribe to
     * @param deviceId  the device identifier (from cookie)
     * @param userId    the authenticated account, or null for a guest
     * @return the SSE emitter for the connection
     * @throws org.danteplanner.backend.planner.exception.PlannerNotFoundException
     *         if no published planner carries the id
     */
    public SseEmitter subscribe(UUID plannerId, UUID deviceId, Long userId) {
        plannerAccessGuard.checkPublished(plannerId);
        SseEmitter emitter = register(plannerId, deviceId, userId);
        log.debug("Comment SSE subscribed: planner={}, device={}", plannerId, deviceId);
        return emitter;
    }

    /**
     * Send a serialized event to the given subscribers of a planner, skipping every connection of an optional account
     * and removing emitters that fail on send.
     *
     * @param plannerId       the planner ID whose subscribers receive the event
     * @param subscribers     the subscriber list to send to
     * @param eventName       the SSE event name
     * @param jsonData        the serialized event payload
     * @param excludeUserId the account to skip, or {@code null} to send to all
     * @return the number of subscribers the event was sent to
     */
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

    /**
     * Broadcast a payload-carrying comment event to every subscriber of a planner.
     *
     * <p>Serializes {@code payload} and sends it under the given event name; dead emitters
     * are removed on send failure. Used by the cross-node fan-out subscriber.</p>
     *
     * @param plannerId the planner ID whose subscribers receive the event
     * @param eventType the SSE event name
     * @param payload   the event payload
     * @param excludeUserId the account whose action raised the event, or null
     */
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

    /**
     * Get the count of active subscribers for a planner.
     *
     * @param plannerId the planner ID
     * @return the number of active SSE connections
     */
    public int getSubscriberCount(UUID plannerId) {
        return connectionCount(plannerId);
    }

    /**
     * Get total connection count across all planners (for monitoring).
     *
     * @return total number of active connections
     */
    public int getTotalConnectionCount() {
        return emitters.values().stream()
                .mapToInt(CopyOnWriteArrayList::size)
                .sum();
    }

    /**
     * Send heartbeat to all connected emitters.
     * Uses different fixedRate to avoid collision with SseService heartbeats.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = HEARTBEAT_INITIAL_DELAY_MS)
    public void sendHeartbeats() {
        heartbeatConnections();
    }

    /**
     * Cleanup zombie connections by probing all emitters.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS, initialDelay = CLEANUP_INITIAL_DELAY_MS)
    public void cleanupZombieConnections() {
        int removed = cleanupConnections();
        if (removed > 0) {
            log.debug("Comment SSE cleanup removed {} zombie connections", removed);
        }
    }

    @Override
    protected void beforeRegister(UUID plannerId, CopyOnWriteArrayList<EmitterEntry> connections) {
        // FIFO eviction if at max capacity (DoS prevention)
        while (connections.size() >= MAX_CONNECTIONS_PER_PLANNER && !connections.isEmpty()) {
            EmitterEntry oldest = connections.remove(0);
            try {
                oldest.emitter().complete();
            } catch (IllegalStateException e) {
                log.debug("Evicted comment SSE emitter for planner {} was already closed: {}",
                        plannerId, e.getMessage());
            }
            log.warn("Comment SSE: Evicted oldest connection for planner {} (max {} reached)",
                    plannerId, MAX_CONNECTIONS_PER_PLANNER);
        }
    }

    @Override
    protected void onConnectedSendFailure(UUID plannerId, UUID deviceId) {
        log.warn("Failed to send connected event for planner {} device {}", plannerId, deviceId);
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
