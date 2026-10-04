package org.danteplanner.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
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
    private final LineageRotationFlag lineageRotationFlag;

    public void logout(String accessToken, String refreshToken) {
        log.info("Processing logout");

        List<LogoutRevocation> revocations = new ArrayList<>();

        if (accessToken != null) {
            try {
                revocations.add(new LogoutRevocation.TokenRevocation(
                        accessToken, tokenValidator.validateAccessToken(accessToken).expiration()));
            } catch (InvalidTokenException e) {
                log.debug("Access token already invalid, skipping blacklist");
            }
        }

        if (refreshToken != null) {
            try {
                TokenClaims refreshClaims = tokenValidator.validateRefreshToken(refreshToken);
                revocations.add(new LogoutRevocation.TokenRevocation(
                        refreshToken, refreshClaims.expiration()));
                if (lineageRotationFlag.isEnabled()) {
                    String familyId = refreshClaims.familyId() != null
                            ? refreshClaims.familyId()
                            : RefreshRotationService.legacyFamilyId(
                                    refreshClaims.userId(), refreshClaims.issuedAt().getTime());
                    revocations.add(new LogoutRevocation.FamilyRevocation(familyId));
                }
            } catch (InvalidTokenException e) {
                log.debug("Refresh token already invalid, skipping blacklist");
            }
        }

        tokenBlacklistService.revokeLogoutSession(revocations);

        log.info("Logout completed");
    }

    public void logoutAll(Long userId, String accessToken) {
        log.info("Processing logout-all for user: {}", userId);

        tokenBlacklistService.invalidateUserTokens(userId);

        if (accessToken != null) {
            try {
                TokenClaims accessClaims = tokenValidator.validateAccessToken(accessToken);
                tokenBlacklistService.blacklistToken(accessToken, accessClaims.expiration());
            } catch (InvalidTokenException e) {
                log.debug("Access token already invalid, skipping blacklist");
            }
        }

        log.info("Logout-all completed for user: {}", userId);
    }

    @Override
    public void endSession(String accessToken, String refreshToken) {
        logout(accessToken, refreshToken);
    }

    @Override
    public void endAllSessions(Long userId) {
        tokenBlacklistService.invalidateUserTokens(userId);
    }
}
