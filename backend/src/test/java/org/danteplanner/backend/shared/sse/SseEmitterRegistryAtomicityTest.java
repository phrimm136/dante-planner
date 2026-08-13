package org.danteplanner.backend.shared.sse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A device's emitter is reachable through the registry from the moment it registers until it
 * unregisters, whatever another device on the same key is doing at that moment.
 *
 * <p>Registration and unregistration each mutate two things — the key's list and, at the edges, the
 * key itself. Run as separate steps they interleave: an emitter can be added to a list that the
 * unregistration on the other thread has already detached from the map, leaving a connected client
 * addressable by nothing until its one-hour timeout expires. Two devices churning the same key is
 * what makes that window observable.</p>
 */
class SseEmitterRegistryAtomicityTest {

    private static final String KEY = "key";
    private static final int ROUNDS = 500;
    private static final long JOIN_TIMEOUT_MS = 30_000;

    @Test
    @DisplayName("a registered emitter stays reachable while a neighbour device unregisters")
    void registry_WhenTwoDevicesChurnOneKey_KeepsEveryLiveEmitterReachable() throws Exception {
        ChurnableSseService service = new ChurnableSseService();
        List<String> unreachable = new CopyOnWriteArrayList<>();
        CyclicBarrier round = new CyclicBarrier(2);

        Thread first = churn(service, round, unreachable);
        Thread second = churn(service, round, unreachable);
        first.start();
        second.start();
        first.join(JOIN_TIMEOUT_MS);
        second.join(JOIN_TIMEOUT_MS);

        assertThat(first.isAlive() || second.isAlive())
                .as("a thread still running means its partner died at the barrier")
                .isFalse();
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
                    service.connect(KEY, deviceId);
                    if (!service.isReachable(KEY, deviceId)) {
                        unreachable.add("device " + deviceId + " round " + i);
                    }
                    service.removeConnection(KEY, deviceId);
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

        private void connect(String key, UUID deviceId) throws IOException {
            register(key, deviceId);
        }

        private boolean isReachable(String key, UUID deviceId) {
            var connections = emitters.get(key);
            return connections != null
                    && connections.stream().anyMatch(entry -> entry.deviceId().equals(deviceId));
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
