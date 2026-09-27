package org.danteplanner.backend.shared.readpath;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ByIdReadGuard {

    public static final String PLANNER_ENTITY_TYPE = "planner";
    public static final String PUBLISHED_PLANNER_SCOPE = "published-planner";

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

    public <T> List<T> readAll(String entityType, Collection<UUID> ids,
            Function<Collection<UUID>, List<T>> dereference, Function<T, UUID> idOf) {
        if (primaryReCheck.isEmpty()) {
            return dereference.apply(ids);
        }
        return primaryReCheck.get().readAllWithReCheck(entityType, ids, dereference, idOf);
    }

    public <T> T readPublicView(String entityType, String withdrawalScope, UUID id, Supplier<T> dereference) {
        T served = read(entityType, id, dereference);
        if (primaryReCheck.isEmpty()) {
            return served;
        }
        return primaryReCheck.get().reReadWhenTombstoned(withdrawalScope, id, served, dereference);
    }
}
