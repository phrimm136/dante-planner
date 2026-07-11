package org.danteplanner.backend.shared.readpath;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Read-path interception seam for by-id dereferences.
 *
 * <p>The pass-through form runs the supplier that dereferences the entity. On Seoul pods (routing +
 * replica enabled) a {@link PrimaryReCheck} is present, so a read-only replica miss is re-checked
 * on the primary before answering; elsewhere the seam stays a pure pass-through. The
 * {@code entityType} and {@code id} arguments carry the forward-compatible signature for routing,
 * staleness re-check, and tombstone handling.</p>
 */
@Component
public class ByIdReadGuard {

    public static final String PLANNER_ENTITY_TYPE = "planner";

    private final Optional<PrimaryReCheck> primaryReCheck;

    public ByIdReadGuard() {
        this(Optional.empty());
    }

    @Autowired
    public ByIdReadGuard(Optional<PrimaryReCheck> primaryReCheck) {
        this.primaryReCheck = primaryReCheck;
    }

    public <T> T read(String entityType, UUID id, Supplier<T> dereference) {
        if (primaryReCheck.isEmpty()) {
            return dereference.get();
        }
        return primaryReCheck.get().readWithReCheck(dereference);
    }
}
