package org.danteplanner.backend.user.service;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.auth.service.AuthenticationService;
import org.springframework.stereotype.Service;

/**
 * Ends a user's session from the account surface, revoking the tokens the request arrived with.
 */
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final AuthenticationService authenticationService;

    /**
     * Revoke the session the request arrived on.
     *
     * @param accessToken  the request's access token, or {@code null}
     * @param refreshToken the request's refresh token, or {@code null}
     */
    public void logout(String accessToken, String refreshToken) {
        authenticationService.logout(accessToken, refreshToken);
    }
}
