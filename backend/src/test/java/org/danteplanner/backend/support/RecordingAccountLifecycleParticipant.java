package org.danteplanner.backend.support;

import java.util.ArrayList;
import java.util.List;

import org.danteplanner.backend.user.api.AccountLifecycleParticipant;

public class RecordingAccountLifecycleParticipant implements AccountLifecycleParticipant {

    public enum Event {
        DEACTIVATED,
        REACTIVATED,
        REASSIGNED_TO_SENTINEL,
        OWNED_DATA_DELETED
    }

    public record Call(RecordingAccountLifecycleParticipant participant, Event event, Long userId,
            Long sentinelUserId) {
    }

    public record Failed(RecordingAccountLifecycleParticipant participant, Event event, Long userId,
            RuntimeException failure) {
    }

    private final List<Object> journal;
    private final ScriptedFailures<Event> failures =
            new ScriptedFailures<>(RecordingAccountLifecycleParticipant.class.getSimpleName(), Event.class);

    public RecordingAccountLifecycleParticipant() {
        this(new ArrayList<>());
    }

    public RecordingAccountLifecycleParticipant(List<Object> journal) {
        this.journal = journal;
    }

    public void failNext(Event event, RuntimeException failure) {
        failures.failNext(event, failure);
    }

    public List<Failed> failed() {
        return journal.stream()
                .filter(Failed.class::isInstance)
                .map(Failed.class::cast)
                .filter(failed -> failed.participant() == this)
                .toList();
    }

    public List<Call> calls() {
        return journal.stream()
                .filter(Call.class::isInstance)
                .map(Call.class::cast)
                .filter(call -> call.participant() == this)
                .toList();
    }

    @Override
    public void accountDeactivated(Long userId) {
        record(Event.DEACTIVATED, userId, null);
    }

    @Override
    public void accountReactivated(Long userId) {
        record(Event.REACTIVATED, userId, null);
    }

    @Override
    public void reassignToSentinel(Long userId, Long sentinelUserId) {
        record(Event.REASSIGNED_TO_SENTINEL, userId, sentinelUserId);
    }

    @Override
    public void deleteOwnedData(Long userId) {
        record(Event.OWNED_DATA_DELETED, userId, null);
    }

    private void record(Event event, Long userId, Long sentinelUserId) {
        failures.throwIfScripted(event, failure -> journal.add(new Failed(this, event, userId, failure)));
        journal.add(new Call(this, event, userId, sentinelUserId));
    }
}
