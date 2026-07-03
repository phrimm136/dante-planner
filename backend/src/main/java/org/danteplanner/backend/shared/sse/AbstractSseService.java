package org.danteplanner.backend.shared.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Template base for SSE services managing per-key emitter connections.
 *
 * <p>Owns the shared connection lifecycle — registration with per-device
 * de-duplication, dead-emitter removal, heartbeat probing, and zombie cleanup —
 * over a registry keyed by {@code K} (user ID or planner ID). Subclasses supply
 * routing, event dispatch, registry-specific behavior (settings cache, capacity
 * eviction), and all logging; log level, message, and logger name stay
 * per-service via the protected hooks.</p>
 *
 * <p>Not a Spring bean. Concrete subclasses carry {@code @Service} and the
 * {@code @Scheduled} entry points, whose intervals differ per service and so
 * cannot live on the (annotation-non-inheriting) base.</p>
 *
 * @param <K> the registry key type ({@code Long} user ID or {@code UUID} planner ID)
 */
public abstract class AbstractSseService<K> {

    protected static final long SSE_TIMEOUT_MS = 3600_000L;

    protected final ConcurrentHashMap<K, CopyOnWriteArrayList<EmitterEntry>> emitters = new ConcurrentHashMap<>();

    protected record EmitterEntry(UUID deviceId, SseEmitter emitter) {}

    /**
     * Register an emitter for a key/device, replacing any prior emitter for the
     * same device and wiring the lifecycle callbacks plus the initial connected event.
     *
     * @param key      the registry key
     * @param deviceId the device identifier
     * @return the registered emitter
     */
    protected SseEmitter register(K key, UUID deviceId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        var connections = emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        connections.removeIf(e -> e.deviceId().equals(deviceId));
        beforeRegister(key, connections);
        connections.add(new EmitterEntry(deviceId, emitter));
        afterRegister(key);

        emitter.onCompletion(() -> removeConnection(key, deviceId));
        emitter.onTimeout(() -> removeConnection(key, deviceId));
        emitter.onError(e -> removeConnection(key, deviceId));

        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (IOException e) {
            onConnectedSendFailure(key, deviceId);
        }

        return emitter;
    }

    /**
     * Remove the connection for a key/device, dropping the key entry once its last
     * connection is gone.
     *
     * @param key      the registry key
     * @param deviceId the device identifier
     */
    public void removeConnection(K key, UUID deviceId) {
        var connections = emitters.get(key);
        if (connections != null) {
            connections.removeIf(e -> e.deviceId().equals(deviceId));
            if (connections.isEmpty()) {
                emitters.remove(key);
                afterKeyRemoved(key);
            }
        }
        onUnsubscribed(key, deviceId);
    }

    protected int connectionCount(K key) {
        var connections = emitters.get(key);
        return connections != null ? connections.size() : 0;
    }

    /**
     * Send a heartbeat comment to every connection, removing any that reject it.
     */
    protected void heartbeatConnections() {
        emitters.forEach((key, connections) -> {
            for (EmitterEntry entry : connections) {
                try {
                    entry.emitter().send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException e) {
                    onHeartbeatFailure(key, entry.deviceId());
                    removeConnection(key, entry.deviceId());
                }
            }
        });
    }

    /**
     * Probe every connection and remove the dead ones.
     *
     * @return the number of connections removed
     */
    protected int cleanupConnections() {
        int removed = 0;
        for (var entry : emitters.entrySet()) {
            K key = entry.getKey();
            for (EmitterEntry connection : entry.getValue()) {
                try {
                    connection.emitter().send(SseEmitter.event().comment("probe"));
                } catch (IOException | IllegalStateException e) {
                    removeConnection(key, connection.deviceId());
                    removed++;
                }
            }
        }
        return removed;
    }

    protected void beforeRegister(K key, CopyOnWriteArrayList<EmitterEntry> connections) {
    }

    protected void afterRegister(K key) {
    }

    protected void afterKeyRemoved(K key) {
    }

    protected abstract void onConnectedSendFailure(K key, UUID deviceId);

    protected abstract void onUnsubscribed(K key, UUID deviceId);

    protected abstract void onHeartbeatFailure(K key, UUID deviceId);
}
