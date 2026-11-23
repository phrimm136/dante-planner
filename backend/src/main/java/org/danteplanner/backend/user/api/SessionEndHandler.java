package org.danteplanner.backend.user.api;

import java.util.List;

public interface SessionEndHandler {

    sealed interface SessionToken {

        record Access(String token) implements SessionToken {
        }

        record Refresh(String token) implements SessionToken {
        }
    }

    void endSession(List<SessionToken> tokens);

    void endAllSessions(Long userId);
}
