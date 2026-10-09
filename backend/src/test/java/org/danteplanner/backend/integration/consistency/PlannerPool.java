package org.danteplanner.backend.integration.consistency;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlannerPool {

    private final List<UUID> keys = new CopyOnWriteArrayList<>();

    void add(UUID key) {
        keys.add(key);
    }

    UUID pick(KeyDraw draw) {
        return draw.from(List.copyOf(keys));
    }

    public int size() {
        return keys.size();
    }
}
