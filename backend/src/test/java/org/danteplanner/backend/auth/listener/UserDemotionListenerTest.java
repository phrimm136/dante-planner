package org.danteplanner.backend.auth.listener;

import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.user.event.UserDemotedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * The credential half of a demotion: the listener that withdraws what the old role was issued.
 */
@ExtendWith(MockitoExtension.class)
class UserDemotionListenerTest {

    private static final Long DEMOTED_USER_ID = 42L;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private UserDemotionListener listener;

    @Test
    @DisplayName("Should invalidate the demoted account's tokens")
    void handleUserDemoted_WhenDemotionCommitted_InvalidatesTheAccountsTokens() {
        listener.handleUserDemoted(new UserDemotedEvent(
                this, DEMOTED_USER_ID, UserRole.ADMIN, UserRole.NORMAL));

        verify(tokenBlacklistService).invalidateUserTokens(DEMOTED_USER_ID);
    }
}
