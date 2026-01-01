package org.danteplanner.backend.service.token;

import org.danteplanner.backend.exception.InvalidTokenException;

/**
 * Interface for JWT token validation.
 * Implements Interface Segregation Principle by separating
 * token validation from generation concerns.
 */
public interface TokenValidator {

    /**
     * Validates a token and extracts its claims.
     *
     * @param token JWT token string to validate
     * @return parsed token claims if valid
     * @throws InvalidTokenException if token is invalid, expired, or malformed
     */
    TokenClaims validateToken(String token);

    /**
     * Extracts user ID from a token.
     *
     * @param token JWT token string
     * @return user ID from token claims
     * @throws InvalidTokenException if token is invalid
     */
    Long getUserIdFromToken(String token);

    /**
     * Checks if a token has expired.
     *
     * @param token JWT token string
     * @return true if token is expired or invalid, false otherwise
     */
    boolean isTokenExpired(String token);
}
