package org.danteplanner.backend.shared.restriction;

public interface RestrictionCheckHandler {

    UnrestrictedMember requireUnrestricted(Long userId);
}
