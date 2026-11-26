package org.danteplanner.backend.shared.config;

import java.util.Optional;

public final class ChosenReadSource {

    private static final ThreadLocal<ReadSourceSelector.Source> CHOSEN = new ThreadLocal<>();

    private ChosenReadSource() {
    }

    public static void record(ReadSourceSelector.Source source) {
        ReadSourceSelector.Source chosen = CHOSEN.get();
        if (chosen == null || (chosen.authority() == ReadSourceSelector.Authority.PRIMARY
                && source.authority() == ReadSourceSelector.Authority.REPLICA)) {
            CHOSEN.set(source);
        }
    }

    public static Optional<ReadSourceSelector.Source> source() {
        return Optional.ofNullable(CHOSEN.get());
    }

    public static void clear() {
        CHOSEN.remove();
    }
}
