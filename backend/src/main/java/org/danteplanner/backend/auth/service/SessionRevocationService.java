package org.danteplanner.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.token.AccessClaims;
import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.RefreshClaims;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.user.api.SessionEndHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Closes sessions: one device's, every device's, and those of an account leaving the service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionRevocationService implements SessionEndHandler {

    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklistService;

    public void logout(List<SessionToken> tokens) {
        log.info("Processing logout");

        List<LogoutRevocation> revocations = new ArrayList<>();
        for (SessionToken token : tokens) {
            switch (token) {
                case SessionToken.Access access -> revocations.addAll(accessRevocations(access.token()));
                case SessionToken.Refresh refresh -> revocations.addAll(refreshRevocations(refresh.token()));
            }
        }

        tokenBlacklistService.revokeLogoutSession(revocations);

        log.info("Logout completed");
    }

    private List<LogoutRevocation> accessRevocations(String accessToken) {
        try {
            return List.of(new LogoutRevocation.TokenRevocation(
                    accessToken, tokenValidator.validateAccessToken(accessToken).expiration()));
        } catch (InvalidTokenException e) {
            log.debug("Access token already invalid, skipping blacklist");
            return List.of();
        }
    }

    private List<LogoutRevocation> refreshRevocations(String refreshToken) {
        try {
            RefreshClaims refreshClaims = tokenValidator.validateRefreshToken(refreshToken);
            return List.of(
                    new LogoutRevocation.TokenRevocation(refreshToken, refreshClaims.expiration()),
                    new LogoutRevocation.FamilyRevocation(refreshClaims.lineage().familyId()));
        } catch (InvalidTokenException e) {
            log.debug("Refresh token already invalid, skipping blacklist");
            return List.of();
        }
    }

    public void logoutAll(Long userId, List<SessionToken> tokens) {
        log.info("Processing logout-all for user: {}", userId);

        tokenBlacklistService.invalidateUserTokens(userId);

        for (SessionToken token : tokens) {
            switch (token) {
                case SessionToken.Access access -> blacklist(access.token());
                case SessionToken.Refresh refresh -> { }
            }
        }

        log.info("Logout-all completed for user: {}", userId);
    }

    private void blacklist(String accessToken) {
        try {
            AccessClaims accessClaims = tokenValidator.validateAccessToken(accessToken);
            tokenBlacklistService.blacklistToken(accessToken, accessClaims.expiration());
        } catch (InvalidTokenException e) {
            log.debug("Access token already invalid, skipping blacklist");
        }
    }

    @Override
    public void endSession(List<SessionToken> tokens) {
        logout(tokens);
    }

    @Override
    public void endAllSessions(Long userId) {
        tokenBlacklistService.invalidateUserTokens(userId);
    }
}
