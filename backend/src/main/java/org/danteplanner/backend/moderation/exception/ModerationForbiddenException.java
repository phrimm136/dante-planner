package org.danteplanner.backend.moderation.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

/**
 * Exception thrown when a moderator or admin attempts an action their authority does not cover:
 * acting on a peer or a superior, or exercising a power reserved to administrators.
 *
 * <p>Carries the message shown to the actor, because every case names a rule the actor can read and
 * act on ("Only administrators can ban users"), not an internal detail.</p>
 */
public class ModerationForbiddenException extends DomainException {

    public ModerationForbiddenException(String message) {
        super(ErrorKind.FORBIDDEN, "FORBIDDEN", message);
    }
}
