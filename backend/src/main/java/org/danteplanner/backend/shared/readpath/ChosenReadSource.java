package org.danteplanner.backend.shared.readpath;

import java.util.Optional;

public final class ChosenReadSource {

    private static final ThreadLocal<Chosen> CHOSEN = new ThreadLocal<>();

    private record Chosen(String name, boolean authoritative) {
    }

    private ChosenReadSource() {
    }

    public static void record(String name, boolean authoritative) {
        Chosen chosen = CHOSEN.get();
        if (chosen == null || (chosen.authoritative() && !authoritative)) {
            CHOSEN.set(new Chosen(name, authoritative));
        }
    }

    public static Optional<String> name() {
        return Optional.ofNullable(CHOSEN.get()).map(Chosen::name);
    }

    public static boolean servedByAuthority() {
        Chosen chosen = CHOSEN.get();
        return chosen == null || chosen.authoritative();
    }

    public static void clear() {
        CHOSEN.remove();
    }
}
