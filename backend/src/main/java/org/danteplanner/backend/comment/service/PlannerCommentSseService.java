package org.danteplanner.backend.comment.service;

import org.danteplanner.backend.shared.sse.AbstractSseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE service for planner comment notifications.
 *
 * <p>Unlike {@link SseService} which is user-centric, this service is planner-centric.
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
    private static final long CLEANUP_INTERVAL_MS = 60_000L; // 1 minute
    private static final int MAX_CONNECTIONS_PER_PLANNER = 500; // Prevent DoS

    private final ObjectMapper objectMapper;

    /**
     * Subscribe a device to receive comment notifications for a planner.
     *
     * @param plannerId the planner ID to subscribe to
     * @param deviceId  the device identifier (from cookie)
     * @return the SSE emitter for the connection
     */
    public SseEmitter subscribe(UUID plannerId, UUID deviceId) {
        SseEmitter emitter = register(plannerId, deviceId);
        log.debug("Comment SSE subscribed: planner={}, device={}", plannerId, deviceId);
        return emitter;
    }

    /**
     * Broadcast a comment:added event to all subscribers of a planner.
     * Excludes the author's device to prevent self-notification.
     *
     * @param plannerId       the planner ID
     * @param excludeDeviceId the device ID to exclude (author's device)
     */
    public void broadcastCommentAdded(UUID plannerId, UUID excludeDeviceId) {
        var subscribers = emitters.get(plannerId);
        if (subscribers == null || subscribers.isEmpty()) {
            log.debug("No subscribers for planner {} comment notification", plannerId);
            return;
        }

        Map<String, Object> payload = Map.of(
                "plannerId", plannerId.toString(),
                "timestamp", Instant.now().toString()
        );

        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize comment:added event for planner {}", plannerId, e);
            return;
        }

        int sent = sendToSubscribers(plannerId, subscribers, "comment:added", jsonData, excludeDeviceId);
        log.debug("Broadcast comment:added to {} subscribers for planner {}", sent, plannerId);
    }

    /**
     * Send a serialized event to the given subscribers of a planner, skipping an optional device
     * and removing emitters that fail on send.
     *
     * @param plannerId       the planner ID whose subscribers receive the event
     * @param subscribers     the subscriber list to send to
     * @param eventName       the SSE event name
     * @param jsonData        the serialized event payload
     * @param excludeDeviceId the device ID to skip, or {@code null} to send to all
     * @return the number of subscribers the event was sent to
     */
    private int sendToSubscribers(UUID plannerId, CopyOnWriteArrayList<EmitterEntry> subscribers,
                                  String eventName, String jsonData, UUID excludeDeviceId) {
        int sent = 0;
        for (EmitterEntry entry : subscribers) {
            if (excludeDeviceId != null && entry.deviceId().equals(excludeDeviceId)) {
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
     */
    public void broadcast(UUID plannerId, String eventType, Object payload) {
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

        sendToSubscribers(plannerId, subscribers, eventType, jsonData, null);
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
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = 5000)
    public void sendHeartbeats() {
        heartbeatConnections();
    }

    /**
     * Cleanup zombie connections by probing all emitters.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS, initialDelay = 30000)
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
            } catch (Exception e) {
                // Ignore completion errors
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
