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

    public void logout(String accessToken, String refreshToken) {
        authenticationService.logout(accessToken, refreshToken);
    }
}
