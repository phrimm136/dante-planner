package org.danteplanner.backend.shared.freshness;

import java.util.Arrays;
import java.util.Set;

public final class FreshnessDeclarations {

    private sealed interface Window {

        record Closed() implements Window {
        }

        record Open(FreshnessDeclaration declaration) implements Window {
        }
    }

    private static final ThreadLocal<Window> DECLARED = ThreadLocal.withInitial(Window.Closed::new);

    private FreshnessDeclarations() {
    }

    public static void begin() {
        DECLARED.set(new Window.Open(new FreshnessDeclaration.Undeclared()));
    }

    public static void declare(FreshnessScope... scopes) {
        if (window() instanceof Window.Open open) {
            DECLARED.set(new Window.Open(open.declaration().declare(Set.copyOf(Arrays.asList(scopes)))));
        }
    }

    public static void declareNone(String reason) {
        if (window() instanceof Window.Open open) {
            DECLARED.set(new Window.Open(open.declaration().declareNone(reason)));
        }
    }

    public static Set<FreshnessScope> declared() {
        return declaration().scopes();
    }

    public static FreshnessDeclaration declaration() {
        return switch (window()) {
            case Window.Closed closed -> new FreshnessDeclaration.Undeclared();
            case Window.Open open -> open.declaration();
        };
    }

    public static boolean isWindowOpen() {
        return window() instanceof Window.Open;
    }

    public static void clear() {
        DECLARED.remove();
    }

    private static Window window() {
        return DECLARED.get();
    }
}
