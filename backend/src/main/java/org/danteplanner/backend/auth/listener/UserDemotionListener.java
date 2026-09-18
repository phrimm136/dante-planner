package org.danteplanner.backend.auth.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.user.event.UserDemotedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDemotionListener {

    private final TokenBlacklistService tokenBlacklistService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserDemoted(UserDemotedEvent event) {
        tokenBlacklistService.invalidateUserTokens(event.getUserId());
        log.info("Invalidated tokens for user {} demoted from {} to {}",
                event.getUserId(), event.getPreviousRole(), event.getNewRole());
    }
}
