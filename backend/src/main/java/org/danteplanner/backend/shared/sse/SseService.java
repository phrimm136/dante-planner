package org.danteplanner.backend.shared.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.entity.UserSettings;
import org.danteplanner.backend.user.service.UserSettingsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.danteplanner.backend.shared.entity.SseEventType;

@Service
@Slf4j
public class SseService extends AbstractSseService<Long> {

    private static final long HEARTBEAT_INTERVAL_MS = SseConstants.USER_STREAM_HEARTBEAT_INTERVAL_MS;

    private static final Duration SETTINGS_CACHE_TTL = Duration.ofMinutes(5);
    private static final long SETTINGS_CACHE_MAX_ENTRIES = 10_000;

    private final ObjectMapper objectMapper;
    private final UserSettingsService userSettingsService;

    public SseService(
            ObjectMapper objectMapper,
            UserSettingsService userSettingsService,
            @Qualifier(SseHeartbeatWorkerConfig.SSE_HEARTBEAT_WORKER) TaskExecutor heartbeatWorker) {
        super(heartbeatWorker);
        this.objectMapper = objectMapper;
        this.userSettingsService = userSettingsService;
    }

    private final Cache<Long, CachedSettings> settingsCache = Caffeine.newBuilder()
            .expireAfterWrite(SETTINGS_CACHE_TTL)
            .maximumSize(SETTINGS_CACHE_MAX_ENTRIES)
            .build();

    private record CachedSettings(
            boolean notifyComments,
            boolean notifyRecommendations,
            boolean notifyNewPublications
    ) {
        static CachedSettings from(UserSettings settings) {
            return new CachedSettings(
                    settings.isNotifyComments(),
                    settings.isNotifyRecommendations(),
                    settings.isNotifyNewPublications()
            );
        }
    }

    public SseEmitter subscribe(Long userId, UUID deviceId) throws IOException {
        SseEmitter emitter = register(userId, deviceId);
        log.info("SSE subscribed: user={}, device={}", userId, deviceId);
        return emitter;
    }

    public void sendToUser(Long userId, String eventType, Object data) {
        sendToUser(userId, null, eventType, data);
    }

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

            if (excludeUserId != null && excludeUserId.equals(userId)) {
                continue;
            }

            if (!isEventAllowed(userId, eventType)) {
                continue;
            }

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

    public void notifyAccountSuspended(Long userId, Object payload) {
        sendToUser(userId, SseEventType.ACCOUNT_SUSPENDED.getValue(), payload);
        log.info("Sent account_suspended notification to user {}", userId);
    }

    public void invalidateSettingsCache(Long userId) {
        settingsCache.invalidate(userId);
        log.debug("Invalidated settings cache for user {}", userId);
    }

    public int getActiveConnectionCount(Long userId) {
        return connectionCount(userId);
    }

    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sweepSseHeartbeats() {
        sweepHeartbeatConnections();
    }

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
    protected void onUnsubscribed(Long userId, UUID deviceId) {
        log.debug("SSE unsubscribed: user={}, device={}", userId, deviceId);
    }

    @Override
    protected void onHeartbeatFailure(Long userId, UUID deviceId) {
        log.debug("Heartbeat failed for user {} device {}, removing emitter", userId, deviceId);
    }

    private boolean isEventAllowed(Long userId, String eventType) {
        SseEventType type = SseEventType.fromValue(eventType);
        if (type == null) {
            log.warn("Unrecognized SSE event type {}, not delivered to user {}", eventType, userId);
            return false;
        }

        CachedSettings settings = cacheSettingsIfAbsent(userId);

        return switch (type) {
            case NOTIFY_COMMENT -> settings.notifyComments();
            case NOTIFY_RECOMMENDED -> settings.notifyRecommendations();
            case NOTIFY_PUBLISHED -> settings.notifyNewPublications();
            case COMMENT_ADDED, SETTINGS_INVALIDATED, ACCOUNT_SUSPENDED -> true;
        };
    }

    private CachedSettings cacheSettingsIfAbsent(Long userId) {
        return settingsCache.get(userId, id -> {
            UserSettings settings = userSettingsService.getOrCreateEntity(id);
            return CachedSettings.from(settings);
        });
    }
}
