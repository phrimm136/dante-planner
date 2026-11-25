package org.danteplanner.backend.planner.precondition;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerForbiddenException;
import org.danteplanner.backend.planner.api.PlannerMiss;
import org.danteplanner.backend.planner.api.PlannerNotFoundException;
import org.danteplanner.backend.planner.repository.PlannerOwnershipRow;
import org.danteplanner.backend.shared.failure.Parsed;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class PlannerOwnershipValidator {

    private record StoredOwner(Long userId, @Nullable Instant deletedAt) {

        static Optional<StoredOwner> of(Optional<PlannerOwnershipRow> row) {
            return row.map(read -> new StoredOwner(read.getUserId(), read.getDeletedAt()));
        }

        boolean isLive() {
            return deletedAt == null;
        }

        boolean isUser(Long id) {
            return userId.equals(id);
        }
    }

    @Parsed
    public record OwnedPlanner(Planner planner) {

        private static OwnedPlanner parse(Planner planner, Long userId) {
            if (!planner.isOwnedBy(userId)) {
                throw new PlannerForbiddenException(planner.getId());
            }
            return new OwnedPlanner(planner);
        }
    }

    @Parsed
    public record LiveOwner(UUID plannerId) {

        private static LiveOwner parse(UUID id, Long userId, Optional<StoredOwner> stored) {
            StoredOwner owner = stored.filter(StoredOwner::isLive).orElseThrow(() -> absent(id));
            if (!owner.isUser(userId)) {
                throw new PlannerForbiddenException(id);
            }
            return new LiveOwner(id);
        }
    }

    @Parsed
    public record LiveOwned(UUID plannerId) {

        private static LiveOwned parse(UUID id, Long userId, Optional<StoredOwner> stored) {
            if (stored.filter(owner -> owner.isUser(userId) && owner.isLive()).isEmpty()) {
                throw absent(id);
            }
            return new LiveOwned(id);
        }
    }

    @Parsed
    public record AvailableId(UUID plannerId) {

        private static AvailableId parse(UUID id, Long userId, Optional<StoredOwner> stored) {
            if (stored.filter(owner -> owner.isUser(userId)).isPresent()) {
                log.warn("Planner {} is soft-deleted for user {} - cannot recreate", id, userId);
                throw absent(id);
            }
            if (stored.filter(StoredOwner::isLive).isPresent()) {
                log.warn("Planner {} exists but belongs to another user (ID collision)", id);
                throw new PlannerForbiddenException(id);
            }
            return new AvailableId(id);
        }
    }

    @Parsed
    public record UpsertableId(UUID plannerId) {

        private static UpsertableId parse(UUID id, Long userId, Optional<StoredOwner> stored) {
            if (stored.filter(owner -> owner.isUser(userId) && owner.isLive()).isPresent()) {
                return new UpsertableId(id);
            }
            return new UpsertableId(AvailableId.parse(id, userId, stored).plannerId());
        }
    }

    public OwnedPlanner requireOwner(Planner planner, Long userId) {
        return OwnedPlanner.parse(planner, userId);
    }

    public LiveOwner requireLiveOwner(UUID id, Long userId, Optional<PlannerOwnershipRow> ownership) {
        return LiveOwner.parse(id, userId, StoredOwner.of(ownership));
    }

    public LiveOwned requireLiveOwnedBy(UUID id, Long userId, Optional<PlannerOwnershipRow> ownership) {
        return LiveOwned.parse(id, userId, StoredOwner.of(ownership));
    }

    public AvailableId requireIdAvailable(UUID id, Long userId, Optional<PlannerOwnershipRow> ownership) {
        return AvailableId.parse(id, userId, StoredOwner.of(ownership));
    }

    public UpsertableId requireUpsertableId(UUID id, Long userId, Optional<PlannerOwnershipRow> ownership) {
        return UpsertableId.parse(id, userId, StoredOwner.of(ownership));
    }

    private static PlannerNotFoundException absent(UUID id) {
        return new PlannerNotFoundException(new PlannerMiss.Absent(id));
    }
}
