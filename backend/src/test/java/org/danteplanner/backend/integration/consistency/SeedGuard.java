package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;

import org.danteplanner.consistency.Delete;
import org.danteplanner.consistency.ListRead;
import org.danteplanner.consistency.Operation;
import org.danteplanner.consistency.PublicRead;
import org.danteplanner.consistency.Publish;
import org.danteplanner.consistency.Read;
import org.danteplanner.consistency.ReadFailed;
import org.danteplanner.consistency.Save;
import org.danteplanner.consistency.Unknown;
import org.danteplanner.consistency.Unpublish;

public final class SeedGuard {

    private static final int NO_RESPONSE = 0;

    private SeedGuard() {
    }

    public static List<String> failures(List<Operation> operations) {
        List<String> failures = new ArrayList<>();
        long unknown = operations.stream().filter(SeedGuard::unknown).count();
        if (unknown > 0) {
            failures.add(unknown + " operations have an unknown outcome, and the run injects no client-side fault");
        }
        return failures;
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
