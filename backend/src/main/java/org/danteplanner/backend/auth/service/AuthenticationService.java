package org.danteplanner.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.user.api.Users;
import org.danteplanner.backend.user.api.dto.UserResponse;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Opening a session against an OAuth provider, and reading the user it belongs to.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final OAuthProviderRegistry providerRegistry;
    private final TokenGenerator tokenGenerator;
    private final Users users;

    /**
     * Result of authentication containing user and token pair.
     */
    public record AuthResult(UserSummary user, String accessToken, String refreshToken, boolean reactivated) {
    }

    public AuthResult authenticateWithOAuth(String providerName, String code,
                                            String redirectUri, String codeVerifier) {
        log.info("Processing OAuth authentication for provider: {}", providerName);

        OAuthProvider provider = providerRegistry.getProvider(providerName);

        OAuthTokens oauthTokens = provider.exchangeCodeForTokens(code, redirectUri, codeVerifier);

        OAuthUserInfo userInfo = provider.getUserInfo(oauthTokens);

        String providerId = userInfo.providerId();
        AuthProviderType providerType = AuthProviderType.fromValue(providerName);
        boolean reactivated = false;

        Optional<UserSummary> activeUser = users.findActiveByProvider(providerType, providerId);

        UserSummary user;
        if (activeUser.isPresent()) {
            user = activeUser.get();
        } else {
            Optional<UserSummary> deletedUser = users.findByProvider(providerType, providerId);

            if (deletedUser.isPresent() && deletedUser.get().deleted()) {
                user = deletedUser.get();
                users.reactivateAccount(user.id());
                reactivated = true;
                log.info("Reactivated soft-deleted account for user: {}", user.id());
            } else if (deletedUser.isPresent()) {
                user = deletedUser.get();
            } else {
                Map<String, String> userInfoMap = Map.of(
                        "id", providerId,
                        "email", userInfo.email()
                );
                user = users.findOrCreateUser(providerName, userInfoMap);
            }
        }

        String accessToken = tokenGenerator.generateAccessToken(user.id(), user.role());
        String refreshToken = tokenGenerator.generateRefreshToken(user.id());

        log.info("User authenticated successfully via {}: userId={} (reactivated: {})",
                providerName, user.id(), reactivated);
        return new AuthResult(user, accessToken, refreshToken, reactivated);
    }

    public UserResponse currentUser(Long userId) {
        return users.describeById(userId);
    }
}
