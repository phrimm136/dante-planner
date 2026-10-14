package org.danteplanner.backend.shared.restriction;

public interface RestrictionCheckHandler {

    void requireUnrestricted(Long userId);
}
