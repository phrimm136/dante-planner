package org.danteplanner.backend.auth.exception;

/**
 * Exception thrown when a refresh token's lineage family has been revoked,
 * either by theft detection or an explicit logout.
 *
 * <p>Internal semantic naming only; it maps to the same HTTP 401 response as
 * {@link TokenRevokedException} so the frontend's existing revocation handling
 * applies unchanged.</p>
 */
public class SessionRevokedException extends RuntimeException {

    private final String familyId;

    /**
     * Creates a new SessionRevokedException.
     *
     * @param familyId the revoked token family identifier
     */
    public SessionRevokedException(String familyId) {
        super(String.format("Refresh token family %s has been revoked", familyId));
        this.familyId = familyId;
    }

    /**
     * Returns the revoked token family identifier.
     */
    public String getFamilyId() {
        return familyId;
    }
}
