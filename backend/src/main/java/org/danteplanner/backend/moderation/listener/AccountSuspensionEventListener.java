package org.danteplanner.backend.moderation.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.moderation.event.AccountSuspendedEvent;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells a restricted user's open sessions about a restriction that committed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountSuspensionEventListener {

    private final SsePublisher ssePublisher;

    /**
     * Announce a committed restriction to the user it restricts.
     *
     * @param event the committed restriction
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAccountSuspended(AccountSuspendedEvent event) {
        ssePublisher.publishAccountSuspended(event.userId(), event.reason(),
                event.suspensionType(), event.durationMinutes());
    }
}
