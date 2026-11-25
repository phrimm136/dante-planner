package org.danteplanner.backend.user.api;

public enum RoleShift {
    PROMOTED,
    DEMOTED,
    SAME;

    public static RoleShift of(UserRole previous, UserRole next) {
        if (previous.outranks(next)) {
            return DEMOTED;
        }
        return next.outranks(previous) ? PROMOTED : SAME;
    }
}
