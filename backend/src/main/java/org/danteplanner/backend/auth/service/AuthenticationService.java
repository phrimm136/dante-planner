package org.danteplanner.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.user.dto.UserResponse;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The session lifecycle: opening one against an OAuth provider, renewing it, and closing it on one
 * device or on all of them.
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
     */
    public record AuthResult(User user, String accessToken, String refreshToken, boolean reactivated) {
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

        Optional<User> activeUser = userService.findActiveByProvider(providerType, providerId);

        User user;
        if (activeUser.isPresent()) {
            user = activeUser.get();
        } else {
            Optional<User> deletedUser = userService.findByProvider(providerType, providerId);

            if (deletedUser.isPresent() && deletedUser.get().isDeleted()) {
                user = deletedUser.get();
                lifecycleService.reactivateAccount(user.getId());
                reactivated = true;
                log.info("Reactivated soft-deleted account for user: {}", user.getId());
            } else if (deletedUser.isPresent()) {
                user = deletedUser.get();
            } else {
                Map<String, String> userInfoMap = Map.of(
                        "id", providerId,
                        "email", userInfo.email()
                );
                user = userService.findOrCreateUser(providerName, userInfoMap);
            }
        }

        String accessToken = tokenGenerator.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = tokenGenerator.generateRefreshToken(user.getId());

        log.info("User authenticated successfully via {}: userId={} (reactivated: {})",
                providerName, user.getId(), reactivated);
        return new AuthResult(user, accessToken, refreshToken, reactivated);
    }

    public UserResponse currentUser(Long userId) {
        return userService.toResponse(userService.findById(userId));
    }

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

}
