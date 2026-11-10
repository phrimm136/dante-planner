package org.danteplanner.backend.shared.outbox.service;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.freshness.DispatchStamp;
import org.danteplanner.backend.shared.sse.SsePublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectPushSynchronizationTest {

    private static final String RAISE = "raise";
    private static final String PUSH = "push";

    private final List<String> recorded = new ArrayList<>();

    private final DispatchStamp stamp = new DispatchStamp() {

        @Override
        public void around(Runnable dispatch) {
            dispatch.run();
        }

        @Override
        public void raise() {
            recorded.add(RAISE);
        }
    };

    private final SsePublisher publisher = new SsePublisher(null, null, null) {

        @Override
        public void publishUserEvent(Long userId, SseEventType type, String entityId, Object payload) {
            recorded.add(PUSH);
        }
    };

    @Test
    void afterCommit_WhenTheDispatchCommitted_RaisesBeforeItFlushesThePushes() {
        EffectPushQueue pushes = new EffectPushQueue(publisher);
        pushes.userEvent(7L, SseEventType.NOTIFY_COMMENT, "notification", null);

        new EffectPushSynchronization(stamp, pushes).afterCommit();

        assertThat(recorded).containsExactly(RAISE, PUSH);
    }

    @Test
    void afterCommit_WhenTheRaiseThrows_StillFlushesThePushesAndPropagates() {
        DispatchStamp throwing = new DispatchStamp() {

            @Override
            public void around(Runnable dispatch) {
                dispatch.run();
            }

            @Override
            public void raise() {
                throw new IllegalStateException("raise failed");
            }
        };
        EffectPushQueue pushes = new EffectPushQueue(publisher);
        pushes.userEvent(7L, SseEventType.NOTIFY_COMMENT, "notification", null);

        assertThatThrownBy(() -> new EffectPushSynchronization(throwing, pushes).afterCommit())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("raise failed");
        assertThat(recorded).containsExactly(PUSH);
    }
}
