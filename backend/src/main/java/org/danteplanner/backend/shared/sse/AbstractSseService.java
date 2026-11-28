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

    private volatile boolean closing;

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
        switch (Phase.of(closing)) {
            case CLOSING -> emitter.complete();
            case SERVING -> open(key, deviceId, subscriber, emitter);
        }
        return emitter;
    }

    private enum Phase {
        SERVING,
        CLOSING;

        static Phase of(boolean closing) {
            return closing ? CLOSING : SERVING;
        }
    }

    private void open(K key, UUID deviceId, Viewer subscriber, SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().name("connected").data("{}"));
        EmitterEntry entry = new EmitterEntry(deviceId, subscriber, emitter);
        emitters.compute(key, (k, existing) -> {
            var connections = existing != null ? existing : new CopyOnWriteArrayList<EmitterEntry>();
            beforeRegister(key, connections);
            connections.add(entry);
            return connections;
        });
        afterRegister(key);

        emitter.onCompletion(() -> remove(key, entry));
        emitter.onTimeout(() -> remove(key, entry));
        emitter.onError(e -> remove(key, entry));

        switch (Phase.of(closing)) {
            case CLOSING -> emitter.complete();
            case SERVING -> { }
        }
    }

    protected void remove(K key, EmitterEntry entry) {
        boolean[] keyRemoved = {false};
        emitters.compute(key, (k, connections) -> {
            if (connections == null) {
                return null;
            }
            connections.remove(entry);
            if (connections.isEmpty()) {
                keyRemoved[0] = true;
                return null;
            }
            return connections;
        });
        if (keyRemoved[0]) {
            afterKeyRemoved(key);
        }
        onUnsubscribed(key, entry.deviceId());
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
            remove(key, entry);
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
                    remove(key, connection);
                    removed++;
                }
            }
        }
        return removed;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        closing = true;
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
