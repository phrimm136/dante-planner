package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.user.api.UserRole;

import java.util.Date;

public record AccessClaims(Long userId, UserRole role, Date issuedAt, Date expiration) {
}
