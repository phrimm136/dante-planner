package org.danteplanner.backend.shared.readpath;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Read-path interception seam for by-id dereferences.
 *
 * <p>In Phase 2 this is a pure pass-through around the supplier that dereferences the entity.
 * Later phases extend it with routing, staleness re-check, and tombstone handling; the
 * {@code entityType} and {@code id} arguments exist for that forward-compatible signature.</p>
 */
@Component
public class ByIdReadGuard {

    public static final String PLANNER_ENTITY_TYPE = "planner";

    public <T> T read(String entityType, UUID id, Supplier<T> dereference) {
        return dereference.get();
    }
}
