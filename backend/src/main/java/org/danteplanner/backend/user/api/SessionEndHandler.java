package org.danteplanner.backend.user.api;

public interface SessionEndHandler {

    void endSession(String accessToken, String refreshToken);

    void endAllSessions(Long userId);
}
