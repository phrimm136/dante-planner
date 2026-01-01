package org.danteplanner.backend.exception;

/**
 * Exception thrown when a token has been revoked/blacklisted.
 * Results in HTTP 401 with code TOKEN_REVOKED.
 */
public class TokenRevokedException extends RuntimeException {

    private final String tokenType;

    /**
     * Creates a new TokenRevokedException.
     *
     * @param tokenType the type of token that was revoked ("access" or "refresh")
     */
    public TokenRevokedException(String tokenType) {
        super(String.format("%s token has been revoked", tokenType));
        this.tokenType = tokenType;
    }

    /**
     * Returns the type of token that was revoked.
     */
    public String getTokenType() {
        return tokenType;
    }
}
