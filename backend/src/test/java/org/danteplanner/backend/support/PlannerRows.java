package org.danteplanner.backend.support;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.planner.entity.Listing;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerModeration;
import org.danteplanner.backend.planner.entity.PlannerPublication;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.Takedown;

import org.danteplanner.backend.planner.repository.PlannerRepository;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.StoredPlanner.StoredContent;
import org.danteplanner.backend.shared.dto.NotificationSetting;

import java.time.Instant;

public final class PlannerRows {

    private PlannerRows() {
    }

    public static StoredPlanner insert(PlannerRepository repository, StoredPlanner planner) {
        repository.insertAggregate(core(planner), content(planner), publication(planner), moderation(planner));
        return planner;
    }

    public static Planner core(StoredPlanner planner) {
        return Planner.created(planner.id(), planner.userId(), planner.plannerType(), planner.createdAt());
    }

    public static PlannerContent content(StoredPlanner planner) {
        StoredContent content = planner.content();
        return new PlannerContent(planner.id(), content.title(), content.status(), content.category(),
                content.keywords(), content.json(), content.schemaVersion(), content.gameContentVersion(),
                content.syncVersion(), content.device().columnValue(), content.lastModifiedAt(), null);
    }

    public static PlannerPublication publication(StoredPlanner planner) {
        boolean notifications = planner.ownerNotifications() == NotificationSetting.ON;
        return switch (planner.publication()) {
            case Publication.Draft draft -> new PlannerPublication(planner.id(), false, null, notifications);
            case Publication.Published(Instant at) -> new PlannerPublication(planner.id(), true, at, notifications);
            case Publication.Unpublished(Instant at) ->
                    new PlannerPublication(planner.id(), false, at, notifications);
        };
    }

    public static PlannerModeration moderation(StoredPlanner planner) {
        @Nullable Instant takenDownAt = switch (planner.takedown()) {
            case Takedown.Clear clear -> null;
            case Takedown.TakenDown(Instant at) -> at;
        };
        return switch (planner.listing()) {
            case Listing.Listed listed -> new PlannerModeration(planner.id(), takenDownAt, false, null, null, null);
            case Listing.Hidden(Long by, String reason, Instant at) ->
                    new PlannerModeration(planner.id(), takenDownAt, true, by, reason, at);
        };
    }
}
