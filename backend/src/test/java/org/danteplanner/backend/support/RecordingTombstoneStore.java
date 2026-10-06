package org.danteplanner.backend.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;

public class RecordingTombstoneStore extends ContentTombstoneStore {

    public enum Operation {
        WRITE_OR_THROW,
        CLEAR_OR_THROW
    }

    public record Tombstone(String entityType, UUID id) {
    }

    public sealed interface Call permits Write, WriteOrThrow, Writes, ClearOrThrow, Failed {
    }

    public record Failed(Operation operation, RuntimeException failure) implements Call {
    }

    public record Write(String entityType, UUID id) implements Call {
    }

    public record WriteOrThrow(String entityType, UUID id) implements Call {
    }

    public record Writes(String entityType, List<UUID> ids) implements Call {
    }

    public record ClearOrThrow(String entityType, UUID id) implements Call {
    }

    private final List<Call> calls = new ArrayList<>();
    private final ScriptedFailures<Operation> failures =
            new ScriptedFailures<>(RecordingTombstoneStore.class.getSimpleName(), Operation.class);

    public RecordingTombstoneStore() {
        super(null, null, new SimpleMeterRegistry());
    }

    public void failNext(Operation operation, RuntimeException failure) {
        failures.failNext(operation, failure);
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    public Set<Tombstone> standing() {
        Set<Tombstone> standing = new LinkedHashSet<>();
        for (Call call : calls) {
            switch (call) {
                case Write write -> standing.add(new Tombstone(write.entityType(), write.id()));
                case WriteOrThrow write -> standing.add(new Tombstone(write.entityType(), write.id()));
                case Writes writes -> writes.ids().forEach(id -> standing.add(new Tombstone(writes.entityType(), id)));
                case ClearOrThrow clear -> standing.remove(new Tombstone(clear.entityType(), clear.id()));
                case Failed failed -> {
                }
            }
        }
        return Set.copyOf(standing);
    }

    @Override
    public void writeTombstone(String entityType, UUID id) {
        calls.add(new Write(entityType, id));
    }

    @Override
    public void writeTombstoneOrThrow(String entityType, UUID id) {
        failures.throwIfScripted(Operation.WRITE_OR_THROW,
                failure -> calls.add(new Failed(Operation.WRITE_OR_THROW, failure)));
        calls.add(new WriteOrThrow(entityType, id));
    }

    @Override
    public void writeTombstones(String entityType, Collection<UUID> ids) {
        if (!ids.isEmpty()) {
            calls.add(new Writes(entityType, List.copyOf(ids)));
        }
    }

    @Override
    public void clearTombstoneOrThrow(String entityType, UUID id) {
        failures.throwIfScripted(Operation.CLEAR_OR_THROW,
                failure -> calls.add(new Failed(Operation.CLEAR_OR_THROW, failure)));
        calls.add(new ClearOrThrow(entityType, id));
    }

    @Override
    public boolean isTombstoned(String entityType, UUID id) {
        return standing().contains(new Tombstone(entityType, id));
    }

    @Override
    public Set<UUID> tombstonedAmong(String entityType, Collection<UUID> ids) {
        Set<Tombstone> standing = standing();
        return ids.stream()
                .filter(id -> standing.contains(new Tombstone(entityType, id)))
                .collect(Collectors.toSet());
    }
}
