package org.danteplanner.backend.auth.facade;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.exception.AccountDeletedException;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.exception.SessionRevokedException;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.RotationResult;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.danteplanner.backend.shared.util.CookieUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Facade for authentication flows.
 * Orchestrates OAuth, token generation, refresh, and logout operations.
 * Separates authentication logic from HTTP concerns in controller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFacade {

    private final OAuthProviderRegistry providerRegistry;
    private final TokenGenerator tokenGenerator;
    private final TokenValidator tokenValidator;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserService userService;
    private final UserAccountLifecycleService lifecycleService;
    private final UserRepository userRepository;
    private final RefreshRotationService refreshRotationService;
    private final CookieUtils cookieUtils;
    private final JwtProperties jwtProperties;
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
        Optional<User> activeUser = userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(
                providerType, providerId);

        User user;
        if (activeUser.isPresent()) {
            // Normal login for active user
            user = activeUser.get();
        } else {
            // 2. Try to find soft-deleted user (for reactivation)
            Optional<User> deletedUser = userRepository.findByProviderAndProviderId(providerType, providerId);

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

        // Clear any previous token invalidation (e.g., from demotion)
        // This allows the user to get fresh, valid tokens on login
        tokenBlacklistService.clearUserInvalidation(user.getId());

        // Generate JWT tokens
        String accessToken = tokenGenerator.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = tokenGenerator.generateRefreshToken(user.getId());

        log.info("User authenticated successfully via {}: userId={} (reactivated: {})",
                providerName, user.getId(), reactivated);
        return new AuthResult(user, accessToken, refreshToken, reactivated);
    }

    /**
     * Refresh tokens using a valid refresh token.
     * Validates the refresh token, blacklists it (rotation), and generates new token pair.
     * Rejects refresh for deleted users.
     *
     * @param refreshToken Current refresh token
     * @param response     HTTP response for setting rotated cookies (lineage path)
     * @return Authentication result with user and new tokens
     * @throws InvalidTokenException if refresh token is invalid or not a refresh token type
     * @throws AccountDeletedException if user account is soft-deleted
     * @throws SessionRevokedException if the token's lineage family has been revoked
     */
    public AuthResult refreshTokens(String refreshToken, HttpServletResponse response) {
        log.info("Processing token refresh");

        if (lineageRotationFlag.isEnabled()) {
            return refreshTokensWithLineage(refreshToken, response);
        }

        // Validate refresh token
        TokenClaims claims = tokenValidator.validateToken(refreshToken);

        // Verify it's a refresh token
        if (!claims.isRefreshToken()) {
            log.warn("Invalid token type for refresh: {}", claims.type());
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_TYPE);
        }

        // Check if token is blacklisted
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            log.warn("Attempted use of blacklisted refresh token for user: {}", claims.userId());
            throw new InvalidTokenException(InvalidTokenException.Reason.REVOKED);
        }

        // Blacklist old refresh token (rotation — grace period allows concurrent requests)
        tokenBlacklistService.blacklistTokenForRotation(refreshToken, claims.expiration());

        // Get user and check if deleted
        User user = userService.findById(claims.userId());
        if (user.isDeleted()) {
            log.warn("Attempted token refresh for deleted user: {}", user.getId());
            throw new AccountDeletedException(user.getId());
        }

        // Generate new tokens (fetch fresh role from user entity)
        String newAccessToken = tokenGenerator.generateAccessToken(user.getId(), user.getRole());
        String newRefreshToken = tokenGenerator.generateRefreshToken(user.getId());

        log.info("Token refreshed successfully for user: {}", user.getId());
        return new AuthResult(user, newAccessToken, newRefreshToken, false);
    }

    /**
     * Refreshes tokens through the lineage rotation service (flag-on path).
     *
     * <p>Delegates rotation and theft detection to {@link RefreshRotationService},
     * which sets the refresh cookie itself. On a successful rotation the access token
     * is minted here with the user's current DB role and set as a cookie.</p>
     *
     * @param refreshToken the presented refresh token
     * @param response     HTTP response the rotation service writes cookies to
     * @return authentication result with user and new tokens
     * @throws SessionRevokedException if the token's family has been revoked
     * @throws InvalidTokenException   if the token is otherwise invalid
     * @throws AccountDeletedException if the user account is soft-deleted
     */
    private AuthResult refreshTokensWithLineage(String refreshToken, HttpServletResponse response) {
        RotationResult result = refreshRotationService.rotate(refreshToken, response);

        if (result instanceof RotationResult.Revoked revoked) {
            log.warn("Refresh rejected: family {} revoked", revoked.familyId());
            throw new SessionRevokedException(revoked.familyId());
        }
        if (result instanceof RotationResult.Rejected rejected) {
            if (rejected.reason() == RotationResult.Rejected.Reason.REVOKED_FAMILY) {
                throw new SessionRevokedException(null);
            }
            throw new InvalidTokenException(InvalidTokenException.Reason.REVOKED);
        }

        RotationResult.Rotated rotated = (RotationResult.Rotated) result;
        TokenClaims claims = rotated.claims();

        User user = userService.findById(claims.userId());
        if (user.isDeleted()) {
            log.warn("Attempted token refresh for deleted user: {}", user.getId());
            throw new AccountDeletedException(user.getId());
        }

        String newAccessToken = tokenGenerator.generateAccessToken(user.getId(), user.getRole());
        cookieUtils.setCookie(response, CookieConstants.ACCESS_TOKEN, newAccessToken,
                jwtProperties.getCookieExpirySeconds());

        log.info("Token refreshed via lineage rotation for user: {}", user.getId());
        return new AuthResult(user, newAccessToken, rotated.newRefreshJwt(), false);
    }

    /**
     * Logout user by blacklisting both tokens.
     *
     * @param accessToken  Access token to blacklist (nullable)
     * @param refreshToken Refresh token to blacklist (nullable)
     */
    public void logout(String accessToken, String refreshToken) {
        log.info("Processing logout");

        // Blacklist access token if present and valid
        if (accessToken != null) {
            try {
                TokenClaims accessClaims = tokenValidator.validateToken(accessToken);
                tokenBlacklistService.blacklistToken(accessToken, accessClaims.expiration());
            } catch (InvalidTokenException e) {
                // Token already expired or invalid - no need to blacklist
                log.debug("Access token already invalid, skipping blacklist");
            }
        }

        // Blacklist refresh token if present and valid
        if (refreshToken != null) {
            try {
                TokenClaims refreshClaims = tokenValidator.validateToken(refreshToken);
                tokenBlacklistService.blacklistToken(refreshToken, refreshClaims.expiration());
                if (lineageRotationFlag.isEnabled() && refreshClaims.familyId() != null) {
                    refreshRotationService.revokeFamily(refreshClaims.familyId());
                }
            } catch (InvalidTokenException e) {
                // Token already expired or invalid - no need to blacklist
                log.debug("Refresh token already invalid, skipping blacklist");
            }
        }

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
                TokenClaims accessClaims = tokenValidator.validateToken(accessToken);
                tokenBlacklistService.blacklistToken(accessToken, accessClaims.expiration());
            } catch (InvalidTokenException e) {
                log.debug("Access token already invalid, skipping blacklist");
            }
        }

        log.info("Logout-all completed for user: {}", userId);
    }

}
