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
import org.danteplanner.backend.shared.failure.Option;
import org.springframework.stereotype.Service;

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

    private record Login(String providerName, AuthProviderType providerType, OAuthUserInfo userInfo) {
    }

    private record Account(UserSummary user, boolean reactivated) {
    }

    private sealed interface AccountCheck {

        record SignIn(UserSummary user) implements AccountCheck {
        }

        record NeedAnyAccount() implements AccountCheck {
        }

        record Reactivate(UserSummary user) implements AccountCheck {
        }

        record Create() implements AccountCheck {
        }

        static AccountCheck ofActive(Option<UserSummary> active) {
            return active.fold(SignIn::new, NeedAnyAccount::new);
        }

        static AccountCheck ofAny(Option<UserSummary> any) {
            return any.fold(AccountCheck::ofExisting, Create::new);
        }

        private static AccountCheck ofExisting(UserSummary user) {
            return user.deleted() ? new Reactivate(user) : new SignIn(user);
        }
    }

    public AuthResult authenticateWithOAuth(String providerName, String code,
                                            String redirectUri, String codeVerifier) {
        log.info("Processing OAuth authentication for provider: {}", providerName);

        OAuthProvider provider = providerRegistry.getProvider(providerName);

        OAuthTokens oauthTokens = OAuthTokens.of(provider.exchangeCodeForTokens(code, redirectUri, codeVerifier));

        OAuthUserInfo userInfo = provider.getUserInfo(oauthTokens);

        String providerId = userInfo.providerId();
        AuthProviderType providerType = AuthProviderType.fromValue(providerName);

        Account account = openAccount(
                AccountCheck.ofActive(users.findActiveByProvider(providerType, providerId)),
                new Login(providerName, providerType, userInfo));
        UserSummary user = account.user();

        String accessToken = tokenGenerator.generateAccessToken(user.id(), user.role());
        String refreshToken = tokenGenerator.generateRefreshToken(user.id());

        log.info("User authenticated successfully via {}: userId={} (reactivated: {})",
                providerName, user.id(), account.reactivated());
        return new AuthResult(user, accessToken, refreshToken, account.reactivated());
    }

    private Account openAccount(AccountCheck check, Login login) {
        return switch (check) {
            case AccountCheck.SignIn signIn -> new Account(signIn.user(), false);
            case AccountCheck.NeedAnyAccount need -> openAccount(AccountCheck.ofAny(
                    users.findByProvider(login.providerType(), login.userInfo().providerId())), login);
            case AccountCheck.Reactivate reactivate -> {
                UserSummary user = reactivate.user();
                users.reactivateAccount(user.id());
                log.info("Reactivated soft-deleted account for user: {}", user.id());
                yield new Account(user, true);
            }
            case AccountCheck.Create create -> new Account(users.findOrCreateUser(
                    login.providerName(), login.userInfo().providerId(), login.userInfo().email()), false);
        };
    }

    public UserResponse currentUser(Long userId) {
        return users.describeById(userId);
    }
}
