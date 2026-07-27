package org.danteplanner.backend.shared.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Central SSE service managing connections and settings-aware event dispatch.
 *
 * <p>Each user can have multiple connected devices. Events are filtered based on
 * user settings cached per connection to avoid DB queries on every event.</p>
 *
 * <p>Event types and their setting checks:
 * <ul>
 *   <li>{@code sync:planner} - requires syncEnabled == true</li>
 *   <li>{@code notify:comment} - requires notifyComments == true</li>
 *   <li>{@code notify:recommended} - requires notifyRecommendations == true</li>
 *   <li>{@code notify:published} - requires notifyNewPublications == true</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseService extends AbstractSseService<Long> {

    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    private static final long CLEANUP_INTERVAL_MS = 60_000L;

    private static final Duration SETTINGS_CACHE_TTL = Duration.ofMinutes(5);
    private static final long SETTINGS_CACHE_MAX_ENTRIES = 10_000;

    private final ObjectMapper objectMapper;
    private final UserSettingsService userSettingsService;

    /**
     * Per-node cache of the settings that gate event delivery. Entries expire on their own so a node
     * that misses an invalidation — its Redis hop dropped, or it joined after the change — serves
     * stale settings for a bounded time instead of until restart.
     */
    private final Cache<Long, CachedSettings> settingsCache = Caffeine.newBuilder()
            .expireAfterWrite(SETTINGS_CACHE_TTL)
            .maximumSize(SETTINGS_CACHE_MAX_ENTRIES)
            .build();

    private record CachedSettings(
            Boolean syncEnabled,
            boolean notifyComments,
            boolean notifyRecommendations,
            boolean notifyNewPublications
    ) {
        static CachedSettings from(UserSettings settings) {
            return new CachedSettings(
                    settings.getSyncEnabled(),
                    settings.isNotifyComments(),
                    settings.isNotifyRecommendations(),
                    settings.isNotifyNewPublications()
            );
        }
    }

    /**
     * Subscribe a device to receive SSE events for a user.
     * Caches user settings for efficient event filtering.
     *
     * @param userId   the user ID
     * @param deviceId the device identifier (UUID)
     * @return the SSE emitter for the connection
     */
    public SseEmitter subscribe(Long userId, UUID deviceId) {
        SseEmitter emitter = register(userId, deviceId);
        log.info("SSE subscribed: user={}, device={}", userId, deviceId);
        return emitter;
    }

    /**
     * Send an event to a user if their settings allow it.
     *
     * @param userId    the user ID
     * @param eventType the event type (e.g., "sync:planner", "notify:comment")
     * @param data      the event data object
     */
    public void sendToUser(Long userId, String eventType, Object data) {
        sendToUser(userId, null, eventType, data);
    }

    /**
     * Send an event to a user if their settings allow it, excluding a specific device.
     *
     * @param userId          the user ID
     * @param excludeDeviceId the device ID to exclude (can be null)
     * @param eventType       the event type
     * @param data            the event data object
     */
    public void sendToUser(Long userId, UUID excludeDeviceId, String eventType, Object data) {
        if (!isEventAllowed(userId, eventType)) {
            log.debug("Event {} blocked by settings for user {}", eventType, userId);
            return;
        }

        var userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event data for type {}", eventType, e);
            return;
        }

        for (EmitterEntry entry : userEmitters) {
            if (excludeDeviceId != null && entry.deviceId().equals(excludeDeviceId)) {
                continue;
            }

            try {
                entry.emitter().send(SseEmitter.event().name(eventType).data(jsonData));
                log.debug("Sent {} to user {} device {}", eventType, userId, entry.deviceId());
            } catch (IOException | IllegalStateException e) {
                log.debug("Failed to send {} to user {} device {}, removing emitter", eventType, userId, entry.deviceId());
                removeConnection(userId, entry.deviceId());
            }
        }
    }

    /**
     * Broadcast an event to all connected users whose settings allow it.
     * Used for site-wide notifications like new planner publications.
     *
     * @param excludeUserId the user ID to exclude (e.g., the author), can be null
     * @param eventType     the event type (e.g., "notify:published")
     * @param data          the event data object
     */
    public void broadcastToAll(Long excludeUserId, String eventType, Object data) {
        String jsonData;
        try {
            jsonData = objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize broadcast data for type {}", eventType, e);
            return;
        }

        int sentCount = 0;
        for (var entry : emitters.entrySet()) {
            Long userId = entry.getKey();

            // Skip excluded user (author)
            if (excludeUserId != null && excludeUserId.equals(userId)) {
                continue;
            }

            // Check user settings
            if (!isEventAllowed(userId, eventType)) {
                continue;
            }

            // Send to all devices for this user
            for (EmitterEntry emitterEntry : entry.getValue()) {
                try {
                    emitterEntry.emitter().send(SseEmitter.event().name(eventType).data(jsonData));
                    sentCount++;
                } catch (IOException | IllegalStateException e) {
                    log.debug("Broadcast failed for user {} device {}, removing emitter",
                            userId, emitterEntry.deviceId());
                    removeConnection(userId, emitterEntry.deviceId());
                }
            }
        }

        log.info("Broadcast {} to {} devices (excluding user {}, total connected users: {})",
                eventType, sentCount, excludeUserId, emitters.size());
    }

    /**
     * Notify a user that their account has been suspended (banned or timed out).
     * This sends an SSE event to all of the user's connected devices.
     *
     * @param userId  the user ID
     * @param payload the suspension detail prepared by the publisher
     */
    public void notifyAccountSuspended(Long userId, Object payload) {
        sendToUser(userId, org.danteplanner.backend.shared.entity.SseEventType.ACCOUNT_SUSPENDED.getValue(), payload);
        log.info("Sent account_suspended notification to user {}", userId);
    }

    /**
     * Invalidate settings cache for a user when their settings change.
     * Next event dispatch will refresh from the database.
     *
     * @param userId the user ID
     */
    public void invalidateSettingsCache(Long userId) {
        settingsCache.invalidate(userId);
        log.debug("Invalidated settings cache for user {}", userId);
    }

    /**
     * Get the count of active connections for a user.
     *
     * @param userId the user ID
     * @return the number of active SSE connections
     */
    public int getActiveConnectionCount(Long userId) {
        return connectionCount(userId);
    }

    /**
     * Send heartbeat to all connected emitters.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeats() {
        heartbeatConnections();
    }

    /**
     * Cleanup zombie connections by probing all emitters.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupZombieConnections() {
        int removed = cleanupConnections();
        if (removed > 0) {
            log.debug("Cleanup removed {} zombie SSE connections", removed);
        }
    }

    @Override
    protected void afterRegister(Long userId) {
        cacheSettingsIfAbsent(userId);
    }

    @Override
    protected void afterKeyRemoved(Long userId) {
        settingsCache.invalidate(userId);
    }

    @Override
    protected void onConnectedSendFailure(Long userId, UUID deviceId) {
        log.warn("Failed to send connected event to user {} device {}", userId, deviceId);
    }

    @Override
    protected void onUnsubscribed(Long userId, UUID deviceId) {
        log.debug("SSE unsubscribed: user={}, device={}", userId, deviceId);
    }

    @Override
    protected void onHeartbeatFailure(Long userId, UUID deviceId) {
        log.debug("Heartbeat failed for user {} device {}, removing emitter", userId, deviceId);
    }

    private boolean isEventAllowed(Long userId, String eventType) {
        CachedSettings settings = cacheSettingsIfAbsent(userId);

        return switch (eventType) {
            case "sync:planner" -> Boolean.TRUE.equals(settings.syncEnabled());
            case "notify:comment" -> settings.notifyComments();
            case "notify:recommended" -> settings.notifyRecommendations();
            case "notify:published" -> settings.notifyNewPublications();
            default -> true;
        };
    }

    private CachedSettings cacheSettingsIfAbsent(Long userId) {
        return settingsCache.get(userId, id -> {
            UserSettings settings = userSettingsService.getOrCreateEntity(id);
            return CachedSettings.from(settings);
        });
    }
}
