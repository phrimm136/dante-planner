package org.danteplanner.backend.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ScriptedFailures<O extends Enum<O>> {

    private final String fake;
    private final Map<O, Deque<RuntimeException>> scripted;

    public ScriptedFailures(String fake, Class<O> operations) {
        this.fake = fake;
        this.scripted = new EnumMap<>(operations);
    }

    public void failNext(O operation, RuntimeException failure) {
        ScriptedFailuresCheck.register(this);
        scripted.computeIfAbsent(operation, key -> new ArrayDeque<>()).add(failure);
    }

    public void throwIfScripted(O operation, Consumer<RuntimeException> journal) {
        Deque<RuntimeException> failures = scripted.get(operation);
        if (failures != null && !failures.isEmpty()) {
            RuntimeException failure = failures.poll();
            journal.accept(failure);
            throw failure;
        }
    }

    List<String> unconsumed() {
        return scripted.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(failure -> fake + " " + entry.getKey().name() + " (" + failure + ")"))
                .toList();
    }
}
