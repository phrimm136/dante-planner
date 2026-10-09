package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;

import org.danteplanner.consistency.Applied;
import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.Found;
import org.danteplanner.consistency.ListRead;
import org.danteplanner.consistency.NotFound;
import org.danteplanner.consistency.Operation;
import org.danteplanner.consistency.PublicRead;
import org.danteplanner.consistency.Publish;
import org.danteplanner.consistency.Read;
import org.danteplanner.consistency.ReadFailed;
import org.danteplanner.consistency.Save;
import org.danteplanner.consistency.Unknown;
import org.danteplanner.consistency.Unpublish;

import static org.danteplanner.backend.integration.consistency.ConsistencyBudget.sessionName;

public final class SeedGuard {

    private static final int NO_RESPONSE = 0;
    private static final String AUTHOR = sessionName(0);

    private SeedGuard() {
    }

    public static List<String> failures(List<Operation> operations, StaleWindow window) {
        List<String> failures = new ArrayList<>();
        long unknown = operations.stream().filter(SeedGuard::unknown).count();
        if (unknown > 0) {
            failures.add(unknown + " operations have an unknown outcome, and phase 1 injects no client-side fault");
        }
        if (!hasStaleWindowPair(operations, window)) {
            failures.add(AUTHOR + " has no Found or NotFound read of a key, completed before phase A's restart, "
                    + "after its own applied save of it invoked at or after phase A's stop");
        }
        return failures;
    }

    private static boolean hasStaleWindowPair(List<Operation> operations, StaleWindow window) {
        List<Save> staleSaves = operations.stream()
                .filter(Save.class::isInstance)
                .map(Save.class::cast)
                .filter(save -> save.session().equals(AUTHOR))
                .filter(save -> save.result() instanceof Applied)
                .filter(save -> save.invokedAt() >= window.stoppedAt())
                .toList();
        return operations.stream()
                .filter(Read.class::isInstance)
                .map(Read.class::cast)
                .filter(read -> read.session().equals(AUTHOR))
                .filter(read -> read.result() instanceof Found || read.result() instanceof NotFound)
                .filter(read -> read.completedAt() < window.restartedAt())
                .anyMatch(read -> staleSaves.stream()
                        .anyMatch(save -> save.key().equals(read.key()) && save.completedAt() < read.invokedAt()));
    }

    private static boolean unknown(Operation operation) {
        return switch (operation) {
            case Save save -> save.result() instanceof Unknown;
            case Delete delete -> delete.result() instanceof Unknown;
            case Read read -> read.result() instanceof ReadFailed failed && failed.status() == NO_RESPONSE;
            case ListRead list -> false;
            case Publish publish -> publish.result() instanceof Unknown;
            case Unpublish unpublish -> unpublish.result() instanceof Unknown;
            case PublicRead read -> read.result() instanceof ReadFailed failed && failed.status() == NO_RESPONSE;
        };
    }
}
