package org.danteplanner.backend.moderation.listener;

import org.danteplanner.backend.moderation.event.AccountSuspendedEvent;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.danteplanner.backend.shared.sse.SuspensionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * The forwarding half of the moved suspension announcement.
 *
 * <p>The service side proves the event is raised. Nothing else proves it is still acted on, and a
 * listener that stops receiving fails no test that only checks what the service published.</p>
 */
@ExtendWith(MockitoExtension.class)
class AccountSuspensionEventListenerTest {

    @Mock
    private SsePublisher ssePublisher;

    @Test
    void handleAccountSuspended_WhenTimeoutDelivered_ForwardsEveryFieldToThePublisher() {
        AccountSuspensionEventListener listener = new AccountSuspensionEventListener(ssePublisher);

        listener.handleAccountSuspended(
                new AccountSuspendedEvent(7L, "Repeated spam", SuspensionType.TIMED_OUT, 30));

        verify(ssePublisher).publishAccountSuspended(7L, "Repeated spam", SuspensionType.TIMED_OUT, 30);
    }

    @Test
    void handleAccountSuspended_WhenBanDelivered_ForwardsTheAbsentDurationUntouched() {
        AccountSuspensionEventListener listener = new AccountSuspensionEventListener(ssePublisher);

        listener.handleAccountSuspended(
                new AccountSuspendedEvent(9L, null, SuspensionType.BAN, null));

        verify(ssePublisher).publishAccountSuspended(9L, null, SuspensionType.BAN, null);
    }
}
