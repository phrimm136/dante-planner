package org.danteplanner.backend.shared.freshness;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FreshnessDeclarations {

    private static final ThreadLocal<Set<FreshnessScope>> DECLARED = new ThreadLocal<>();

    private FreshnessDeclarations() {
    }

    public static void begin() {
        DECLARED.set(new LinkedHashSet<>());
    }

    public static void declare(FreshnessScope... scopes) {
        Set<FreshnessScope> declared = DECLARED.get();
        if (declared != null) {
            Collections.addAll(declared, scopes);
        }
    }

    public static Set<FreshnessScope> declared() {
        Set<FreshnessScope> declared = DECLARED.get();
        return declared == null ? Set.of() : Set.copyOf(declared);
    }

    public static void clear() {
        DECLARED.remove();
    }
}
