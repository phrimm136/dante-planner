package org.danteplanner.backend.support;

import java.util.ArrayList;
import java.util.List;

import org.danteplanner.backend.user.api.SessionEndHandler;

public class RecordingSessionEndHandler implements SessionEndHandler {

    public enum Operation {
        END_SESSION,
        END_ALL_SESSIONS
    }

    public sealed interface Call permits EndSession, EndAllSessions, Failed {
    }

    public record Failed(Operation operation, RuntimeException failure) implements Call {
    }

    public record EndSession(String accessToken, String refreshToken) implements Call {
    }

    public record EndAllSessions(Long userId) implements Call {
    }

    private final List<Object> journal;
    private final ScriptedFailures<Operation> failures =
            new ScriptedFailures<>(RecordingSessionEndHandler.class.getSimpleName(), Operation.class);

    public RecordingSessionEndHandler() {
        this(new ArrayList<>());
    }

    public RecordingSessionEndHandler(List<Object> journal) {
        this.journal = journal;
    }

    public void failNext(Operation operation, RuntimeException failure) {
        failures.failNext(operation, failure);
    }

    public List<Call> calls() {
        return journal.stream()
                .filter(Call.class::isInstance)
                .map(Call.class::cast)
                .toList();
    }

    @Override
    public void endSession(String accessToken, String refreshToken) {
        failures.throwIfScripted(Operation.END_SESSION,
                failure -> journal.add(new Failed(Operation.END_SESSION, failure)));
        journal.add(new EndSession(accessToken, refreshToken));
    }

    @Override
    public void endAllSessions(Long userId) {
        failures.throwIfScripted(Operation.END_ALL_SESSIONS,
                failure -> journal.add(new Failed(Operation.END_ALL_SESSIONS, failure)));
        journal.add(new EndAllSessions(userId));
    }
}
