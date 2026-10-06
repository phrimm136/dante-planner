package org.danteplanner.backend.moderation.listener;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.moderation.event.AccountSuspendedEvent;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SseChannel;
import org.danteplanner.backend.shared.sse.SseEnvelope;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.sse.SuspensionType;
import org.danteplanner.backend.support.RecordingSseChannelSender;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The forwarding half of the moved suspension announcement.
 *
 * <p>The service side proves the event is raised. Nothing else proves it is still acted on, and a
 * listener that stops receiving fails no test that only checks what the service published.</p>
 */
class AccountSuspensionEventListenerTest {

    private final RecordingSseChannelSender sseChannel = new RecordingSseChannelSender();

    private final SsePublisher ssePublisher =
            new SsePublisher(sseChannel, Jackson2ObjectMapperBuilder.json().build(), new SimpleMeterRegistry());

    @Test
    void handleAccountSuspended_WhenTimeoutDelivered_ForwardsEveryFieldToThePublisher() {
        AccountSuspensionEventListener listener = new AccountSuspensionEventListener(ssePublisher);

        listener.handleAccountSuspended(
                new AccountSuspendedEvent(7L, "Repeated spam", SuspensionType.TIMED_OUT, 30));

        assertThat(sseChannel.envelopes(SseChannel.USER)).containsExactly(new SseEnvelope(
                SseEventType.ACCOUNT_SUSPENDED, 7L, null, null, null, null,
                Map.of("suspensionType", "TIMEOUT", "reason", "Repeated spam", "durationMinutes", 30)));
    }
}
