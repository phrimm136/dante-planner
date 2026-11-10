package org.danteplanner.backend.shared.freshness;

import java.util.Arrays;
import java.util.Set;

public final class FreshnessDeclarations {

    private static final ThreadLocal<FreshnessDeclaration> DECLARED = new ThreadLocal<>();

    private FreshnessDeclarations() {
    }

    public static void begin() {
        DECLARED.set(new FreshnessDeclaration.Undeclared());
    }

    public static void declare(FreshnessScope... scopes) {
        FreshnessDeclaration declared = DECLARED.get();
        if (declared != null) {
            DECLARED.set(declared.declare(Set.copyOf(Arrays.asList(scopes))));
        }
    }

    public static void declareNone(String reason) {
        FreshnessDeclaration declared = DECLARED.get();
        if (declared != null) {
            DECLARED.set(declared.declareNone(reason));
        }
    }

    public static Set<FreshnessScope> declared() {
        return declaration().scopes();
    }

    public static FreshnessDeclaration declaration() {
        FreshnessDeclaration declared = DECLARED.get();
        return declared == null ? new FreshnessDeclaration.Undeclared() : declared;
    }

    public static void clear() {
        DECLARED.remove();
    }
}
