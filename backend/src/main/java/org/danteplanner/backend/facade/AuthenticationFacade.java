package org.danteplanner.backend.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.danteplanner.backend.service.UserService;
import org.danteplanner.backend.service.oauth.OAuthProvider;
import org.danteplanner.backend.service.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.service.oauth.OAuthTokens;
import org.danteplanner.backend.service.oauth.OAuthUserInfo;
import org.danteplanner.backend.service.token.TokenBlacklistService;
import org.danteplanner.backend.service.token.TokenClaims;
import org.danteplanner.backend.service.token.TokenGenerator;
import org.danteplanner.backend.service.token.TokenValidator;
import org.springframework.stereotype.Service;

import java.util.Map;

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

    /**
     * Result of authentication containing user and token pair.
     *
     * @param user         Authenticated user entity
     * @param accessToken  JWT access token
     * @param refreshToken JWT refresh token
     */
    public record AuthResult(User user, String accessToken, String refreshToken) {
    }

    /**
     * Authenticate user via OAuth provider.
     * Exchanges authorization code for tokens, retrieves user info,
     * finds or creates user, and generates JWT token pair.
     *
     * @param providerName OAuth provider name (e.g., "google")
     * @param code         Authorization code from OAuth callback
     * @param redirectUri  Redirect URI used in authorization request
     * @param codeVerifier PKCE code verifier
     * @return Authentication result with user and tokens
     */
    public AuthResult authenticateWithOAuth(String providerName, String code,
                                            String redirectUri, String codeVerifier) {
        log.info("Processing OAuth authentication for provider: {}", providerName);

        // Get provider from registry
        OAuthProvider provider = providerRegistry.getProvider(providerName);

        // Exchange code for OAuth tokens
        OAuthTokens oauthTokens = provider.exchangeCodeForTokens(code, redirectUri, codeVerifier);

        // Get user info from provider
        OAuthUserInfo userInfo = provider.getUserInfo(oauthTokens.accessToken());

        // Find or create user
        Map<String, String> userInfoMap = Map.of(
                "id", userInfo.providerId(),
                "email", userInfo.email()
        );
        User user = userService.findOrCreateUser(providerName, userInfoMap);

        // Generate JWT tokens
        String accessToken = tokenGenerator.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = tokenGenerator.generateRefreshToken(user.getId(), user.getEmail());

        log.info("User authenticated successfully via {}: {}", providerName, user.getEmail());
        return new AuthResult(user, accessToken, refreshToken);
    }

    /**
     * Refresh tokens using a valid refresh token.
     * Validates the refresh token, blacklists it (rotation), and generates new token pair.
     *
     * @param refreshToken Current refresh token
     * @return Authentication result with user and new tokens
     * @throws InvalidTokenException if refresh token is invalid or not a refresh token type
     */
    public AuthResult refreshTokens(String refreshToken) {
        log.info("Processing token refresh");

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

        // Blacklist old refresh token (rotation)
        tokenBlacklistService.blacklistToken(refreshToken, claims.expiration());

        // Get user
        User user = userService.findById(claims.userId());

        // Generate new tokens
        String newAccessToken = tokenGenerator.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = tokenGenerator.generateRefreshToken(user.getId(), user.getEmail());

        log.info("Token refreshed successfully for user: {}", user.getEmail());
        return new AuthResult(user, newAccessToken, newRefreshToken);
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
            } catch (InvalidTokenException e) {
                // Token already expired or invalid - no need to blacklist
                log.debug("Refresh token already invalid, skipping blacklist");
            }
        }

        log.info("Logout completed");
    }

}
