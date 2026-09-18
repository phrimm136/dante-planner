package org.danteplanner.backend.shared.readpath;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
        return primaryReCheck.get().readWithReCheck(entityType, id, dereference);
    }
}
