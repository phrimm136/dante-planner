package org.danteplanner.backend.support;

import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.Publication;
import org.danteplanner.backend.planner.entity.Takedown;

import org.danteplanner.backend.planner.repository.ContentWrite;
import org.danteplanner.backend.planner.repository.StoredPlanner;
import org.danteplanner.backend.planner.repository.StoredPlanner.StoredContent;

import java.time.Instant;
import java.util.UUID;

public final class PlannerRows {

    private PlannerRows() {
    }

    public static StoredPlanner insert(PlannerSeeds seeds, StoredPlanner planner) {
        UUID id = planner.id();
        seeds.transactions().executeWithoutResult(status -> {
            seeds.planners().insertAggregate(core(planner), content(planner));
            switch (planner.publication()) {
                case Publication.Draft draft -> { }
                case Publication.Published(Instant at) -> oneRow(seeds.publications().publishFirst(id, at));
                case Publication.Unpublished unpublished ->
                        throw new UnsupportedOperationException("no test seeds an unpublished planner");
            }
            switch (planner.takedown()) {
                case Takedown.Clear clear -> { }
                case Takedown.TakenDown takenDown ->
                        throw new UnsupportedOperationException("no test seeds a taken-down planner");
            }
        });
        return planner;
    }

    private static Planner core(StoredPlanner planner) {
        return Planner.created(planner.id(), planner.userId(), planner.plannerType(), planner.createdAt());
    }

    private static PlannerContent content(StoredPlanner planner) {
        StoredContent content = planner.content();
        return PlannerContent.created(planner.id(), new ContentWrite(content.title(), content.status(),
                content.category(), content.keywords(), content.json(), content.schemaVersion(),
                content.gameContentVersion(), content.device(), content.syncVersion(), content.lastModifiedAt()));
    }

    private static void oneRow(int rows) {
        if (rows != 1) {
            throw new IllegalStateException("a seeding write matched " + rows + " rows, not 1");
        }
    }
}
