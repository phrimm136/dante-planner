package org.danteplanner.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

/**
 * The session lifecycle: opening one against an OAuth provider, renewing it, and closing it on one
 * device or on all of them.
 *
 * <p>Its collaborators are many because a session touches many parts, but they serve one
 * responsibility, and a change to the token format reaches every operation here alike.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final OAuthProviderRegistry providerRegistry;
    private final TokenGenerator tokenGenerator;
    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserService userService;
    private final UserAccountLifecycleService lifecycleService;
    private final LineageRotationFlag lineageRotationFlag;

    /**
     * Result of authentication containing user and token pair.
     *
     * @param user         Authenticated user entity
     * @param accessToken  JWT access token
     * @param refreshToken JWT refresh token
     * @param reactivated  Whether the account was reactivated from soft-deleted state
     */
    public record AuthResult(User user, String accessToken, String refreshToken, boolean reactivated) {
    }

    /**
     * Authenticate user via OAuth provider.
     * Exchanges authorization code for tokens, retrieves user info,
     * finds or creates user, and generates JWT token pair.
     * If the user was previously soft-deleted, reactivates their account.
     *
     * @param providerName OAuth provider name (e.g., "google")
     * @param code         Authorization code from OAuth callback
     * @param redirectUri  Redirect URI used in authorization request
     * @param codeVerifier PKCE code verifier
     * @return Authentication result with user, tokens, and reactivation status
     */
    public AuthResult authenticateWithOAuth(String providerName, String code,
                                            String redirectUri, String codeVerifier) {
        log.info("Processing OAuth authentication for provider: {}", providerName);

        // Get provider from registry
        OAuthProvider provider = providerRegistry.getProvider(providerName);

        // Exchange code for OAuth tokens
        OAuthTokens oauthTokens = provider.exchangeCodeForTokens(code, redirectUri, codeVerifier);

        // Get user info — provider extracts from id_token if available, else network call
        OAuthUserInfo userInfo = provider.getUserInfo(oauthTokens);

        String providerId = userInfo.providerId();
        AuthProviderType providerType = AuthProviderType.fromValue(providerName);
        boolean reactivated = false;

        // 1. Try to find active user
        Optional<User> activeUser = userService.findActiveByProvider(providerType, providerId);

        User user;
        if (activeUser.isPresent()) {
            // Normal login for active user
            user = activeUser.get();
        } else {
            // 2. Try to find soft-deleted user (for reactivation)
            Optional<User> deletedUser = userService.findByProvider(providerType, providerId);

            if (deletedUser.isPresent() && deletedUser.get().isDeleted()) {
                // Reactivate the soft-deleted account
                user = deletedUser.get();
                lifecycleService.reactivateAccount(user.getId());
                reactivated = true;
                log.info("Reactivated soft-deleted account for user: {}", user.getId());
            } else if (deletedUser.isPresent()) {
                // User exists but not deleted - use as-is
                user = deletedUser.get();
            } else {
                // 3. Create new user
                Map<String, String> userInfoMap = Map.of(
                        "id", providerId,
                        "email", userInfo.email()
                );
                user = userService.findOrCreateUser(providerName, userInfoMap);
            }
        }

        // Generate JWT tokens
        String accessToken = tokenGenerator.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = tokenGenerator.generateRefreshToken(user.getId());

        log.info("User authenticated successfully via {}: userId={} (reactivated: {})",
                providerName, user.getId(), reactivated);
        return new AuthResult(user, accessToken, refreshToken, reactivated);
    }

    /**
     * Logout user by blacklisting both tokens.
     *
     * @param accessToken  Access token to blacklist (nullable)
     * @param refreshToken Refresh token to blacklist (nullable)
     */
    public void logout(String accessToken, String refreshToken) {
        log.info("Processing logout");

        String validAccessToken = null;
        Date accessExpiry = null;
        if (accessToken != null) {
            try {
                accessExpiry = tokenValidator.validateAccessToken(accessToken).expiration();
                validAccessToken = accessToken;
            } catch (InvalidTokenException e) {
                log.debug("Access token already invalid, skipping blacklist");
            }
        }

        String validRefreshToken = null;
        Date refreshExpiry = null;
        String familyId = null;
        if (refreshToken != null) {
            try {
                TokenClaims refreshClaims = tokenValidator.validateRefreshToken(refreshToken);
                refreshExpiry = refreshClaims.expiration();
                validRefreshToken = refreshToken;
                if (lineageRotationFlag.isEnabled()) {
                    // A legacy token carries no family, but admission synthesizes one
                    // deterministically, so the same value is revocable here.
                    familyId = refreshClaims.familyId() != null
                            ? refreshClaims.familyId()
                            : RefreshRotationService.legacyFamilyId(
                                    refreshClaims.userId(), refreshClaims.issuedAt().getTime());
                }
            } catch (InvalidTokenException e) {
                log.debug("Refresh token already invalid, skipping blacklist");
            }
        }

        tokenBlacklistService.revokeLogoutSession(
                validAccessToken, accessExpiry, validRefreshToken, refreshExpiry, familyId);

        log.info("Logout completed");
    }

    /**
     * Logs the user out of every device by invalidating all tokens issued for them.
     *
     * <p>Marks the user's tokens invalid via {@link TokenBlacklistService#invalidateUserTokens(Long)}
     * so any token issued before now is rejected at the filter, and immediately blacklists the
     * current request's access token (no grace period). Existing lineage rotation entries are left
     * untouched — they become irrelevant because the user-wide invalidation check rejects them first.</p>
     *
     * @param userId      the authenticated user whose sessions are being terminated
     * @param accessToken the current request's access token to blacklist immediately (nullable)
     */
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

}
