package org.danteplanner.backend.auth.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.user.event.UserDemotedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Withdraws the credentials a demoted account still holds.
 *
 * <p>Access tokens carry the role in their claims and the filter authenticates from claims alone,
 * so a demoted user keeps the old role until the token expires unless it is invalidated here.</p>
 *
 * <p>Runs AFTER_COMMIT, which leaves a window between the committed role change and the
 * invalidation. It is same-thread and sub-millisecond, and the alternative — invalidating before
 * commit — logs the user out even when the demotion rolls back.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserDemotionListener {

    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Invalidate every token issued to the demoted account.
     *
     * @param event the committed demotion
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserDemoted(UserDemotedEvent event) {
        tokenBlacklistService.invalidateUserTokens(event.getUserId());
        log.info("Invalidated tokens for user {} demoted from {} to {}",
                event.getUserId(), event.getPreviousRole(), event.getNewRole());
    }
}
