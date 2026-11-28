package org.danteplanner.backend.shared.sse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.danteplanner.backend.shared.failure.Option;
import org.danteplanner.backend.shared.security.Viewer;
import org.danteplanner.backend.support.Hang;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A connection's emitter is reachable through the registry from the moment it registers until it
 * unregisters, whatever another connection on the same key is doing at that moment.
 *
 * <p>Registration and unregistration each mutate two things — the key's list and, at the edges, the
 * key itself. Run as separate steps they interleave: an emitter can be added to a list that the
 * unregistration on the other thread has already detached from the map, leaving a connected client
 * addressable by nothing until its one-hour timeout expires. Two connections churning the same key is
 * what makes that window observable.</p>
 */
class SseEmitterRegistryAtomicityTest {

    private static final String KEY = "key";
    private static final int ROUNDS = 500;

    @Test
    void registry_WhenTwoDevicesChurnOneKey_KeepsEveryLiveEmitterReachable() {
        ChurnableSseService service = new ChurnableSseService();
        List<String> unreachable = new CopyOnWriteArrayList<>();
        CyclicBarrier round = new CyclicBarrier(2);

        Thread first = churn(service, round, unreachable);
        Thread second = churn(service, round, unreachable);
        first.start();
        second.start();
        Hang.ended(first, "the first device's churn (a thread still running means its partner died at the barrier)");
        Hang.ended(second, "the second device's churn (a thread still running means its partner died at the barrier)");

        assertThat(unreachable)
                .as("each of these registered an emitter the registry could not then find")
                .isEmpty();
        assertThat(service.holds(KEY))
                .as("the last unregistration drops the key")
                .isFalse();
    }

    private static Thread churn(ChurnableSseService service, CyclicBarrier round, List<String> unreachable) {
        UUID deviceId = UUID.randomUUID();
        return new Thread(() -> {
            for (int i = 0; i < ROUNDS; i++) {
                try {
                    round.await();
                    SseEmitter emitter = service.connect(KEY, deviceId);
                    switch (service.entryOf(KEY, emitter)) {
                        case Option.Some<AbstractSseService.EmitterEntry>(var entry) -> service.remove(KEY, entry);
                        case Option.None<AbstractSseService.EmitterEntry> _ ->
                                unreachable.add("device " + deviceId + " round " + i);
                    }
                } catch (IOException | BrokenBarrierException e) {
                    unreachable.add("device " + deviceId + " round " + i + ": " + e);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    /** Opens the registry lifecycle the base class keeps protected. */
    private static final class ChurnableSseService extends AbstractSseService<String> {

        private ChurnableSseService() {
            super(Runnable::run);
        }

        private SseEmitter connect(String key, UUID deviceId) throws IOException {
            return register(key, deviceId, new Viewer.Guest());
        }

        private Option<EmitterEntry> entryOf(String key, SseEmitter emitter) {
            return Option.fromNullable(emitters.get(key))
                    .flatMap(connections -> Option.from(
                            connections.stream().filter(entry -> entry.emitter().equals(emitter)).findFirst()));
        }

        private boolean holds(String key) {
            return emitters.containsKey(key);
        }

        @Override
        protected void onUnsubscribed(String key, UUID deviceId) {
        }

        @Override
        protected void onHeartbeatFailure(String key, UUID deviceId) {
        }
    }
}
