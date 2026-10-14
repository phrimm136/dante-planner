package org.danteplanner.backend.moderation.api;

public interface RestrictionCheck {

    void requireUnrestricted(Long userId);

    void recordPrivate();
}
