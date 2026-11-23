package org.danteplanner.backend.shared.security;

import org.jspecify.annotations.Nullable;

public sealed interface Viewer {

    record Guest() implements Viewer {
    }

    record Member(Long userId) implements Viewer {
    }

    static Viewer of(@Nullable Long principal) {
        return principal == null ? new Guest() : new Member(principal);
    }

    default Member requireMember() {
        return switch (this) {
            case Guest guest -> throw new IllegalStateException("a member route was reached without a signed-in user");
            case Member member -> member;
        };
    }

    default boolean is(Long userId) {
        return switch (this) {
            case Guest guest -> false;
            case Member member -> member.userId().equals(userId);
        };
    }
}
