package org.danteplanner.backend.auth.exception;

import lombok.Getter;
import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

/**
 * Exception thrown when a refresh token's lineage family has been revoked,
 * either by theft detection or an explicit logout.
 *
 * <p>Internal semantic naming only; it maps to the same HTTP 401 response as
 * {@link TokenRevokedException} so the frontend's existing revocation handling
 * applies unchanged.</p>
 */
@Getter
public class SessionRevokedException extends DomainException {

    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final String CLIENT_DETAIL = "Authentication required";

    private final String familyId;

    /**
     * Creates a new SessionRevokedException.
     *
     * @param familyId the revoked token family identifier
     */
    public SessionRevokedException(String familyId) {
        super(ErrorKind.UNAUTHENTICATED, ERROR_CODE, CLIENT_DETAIL);
        this.familyId = familyId;
    }

    /**
     * Creates a new SessionRevokedException for a revocation whose family the rejection
     * did not name.
     */
    public SessionRevokedException() {
        this((String) null);
    }

    @Override
    public String getMessage() {
        return familyId == null
                ? "Refresh token family has been revoked"
                : String.format("Refresh token family %s has been revoked", familyId);
    }

    @Override
    public boolean reportable() {
        return true;
    }
}
