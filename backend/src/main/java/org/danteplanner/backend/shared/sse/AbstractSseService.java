package org.danteplanner.backend.shared.sse;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.security.Viewer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public abstract class AbstractSseService<K> implements ApplicationListener<ContextClosedEvent> {

    protected static final long SSE_TIMEOUT_MS = 3600_000L;

    protected static final long CLEANUP_INTERVAL_MS = 60_000L;

    protected final ConcurrentHashMap<K, CopyOnWriteArrayList<EmitterEntry>> emitters = new ConcurrentHashMap<>();

    private final TaskExecutor heartbeatWorker;

    private volatile Phase phase = Phase.SERVING;

    protected AbstractSseService(TaskExecutor heartbeatWorker) {
        this.heartbeatWorker = heartbeatWorker;
    }

    /**
     * {@code deviceId} is client-supplied; the subscriber comes from the authenticated principal, so
     * only it is safe to address or exclude by.
     */
    protected record EmitterEntry(UUID deviceId, Viewer subscriber, SseEmitter emitter) {}

    protected SseEmitter register(K key, UUID deviceId, Viewer subscriber) throws IOException {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        switch (phase) {
            case CLOSING -> emitter.complete();
            case SERVING -> open(key, deviceId, subscriber, emitter);
        }
        return emitter;
    }

    private enum Phase {
        SERVING,
        CLOSING
    }

    private void open(K key, UUID deviceId, Viewer subscriber, SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().name("connected").data("{}"));
        emitters.compute(key, (k, existing) -> {
            var connections = existing != null ? existing : new CopyOnWriteArrayList<EmitterEntry>();
            connections.removeIf(e -> e.deviceId().equals(deviceId));
            beforeRegister(key, connections);
            connections.add(new EmitterEntry(deviceId, subscriber, emitter));
            return connections;
        });
        afterRegister(key);

        emitter.onCompletion(() -> removeConnection(key, deviceId));
        emitter.onTimeout(() -> removeConnection(key, deviceId));
        emitter.onError(e -> removeConnection(key, deviceId));

        switch (phase) {
            case CLOSING -> emitter.complete();
            case SERVING -> { }
        }
    }

    public void removeConnection(K key, UUID deviceId) {
        boolean[] keyRemoved = {false};
        emitters.compute(key, (k, connections) -> {
            if (connections == null) {
                return null;
            }
            connections.removeIf(e -> e.deviceId().equals(deviceId));
            if (connections.isEmpty()) {
                keyRemoved[0] = true;
                return null;
            }
            return connections;
        });
        if (keyRemoved[0]) {
            afterKeyRemoved(key);
        }
        onUnsubscribed(key, deviceId);
    }

    protected int connectionCount(K key) {
        var connections = emitters.get(key);
        return connections != null ? connections.size() : 0;
    }

    /**
     * An SSE send blocks for as long as the peer's receive window stays full.
     */
    protected void sweepHeartbeatConnections() {
        emitters.forEach((key, connections) -> {
            for (EmitterEntry entry : connections) {
                heartbeatWorker.execute(() -> sendHeartbeat(key, entry));
            }
        });
    }

    private void sendHeartbeat(K key, EmitterEntry entry) {
        try {
            entry.emitter().send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            onHeartbeatFailure(key, entry.deviceId());
            removeConnection(key, entry.deviceId());
        }
    }

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

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        phase = Phase.CLOSING;
        emitters.values().forEach(connections -> connections.forEach(AbstractSseService::complete));
    }

    private static void complete(EmitterEntry entry) {
        try {
            entry.emitter().complete();
        } catch (IllegalStateException e) {
            log.debug("Completing SSE emitter for device {} on shutdown failed", entry.deviceId(), e);
        }
    }

    protected void beforeRegister(K key, CopyOnWriteArrayList<EmitterEntry> connections) {
    }

    protected void afterRegister(K key) {
    }

    protected void afterKeyRemoved(K key) {
    }

    protected abstract void onUnsubscribed(K key, UUID deviceId);

    protected abstract void onHeartbeatFailure(K key, UUID deviceId);
}
