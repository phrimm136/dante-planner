package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.user.api.UserRole;

public interface TokenGenerator {

    String generateAccessToken(Long userId, UserRole role);

    String generateRefreshToken(Long userId);

    String generateRefreshToken(Long userId, String familyId, String parentJti);
}
