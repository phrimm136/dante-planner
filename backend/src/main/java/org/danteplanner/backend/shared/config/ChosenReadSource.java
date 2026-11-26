package org.danteplanner.backend.shared.config;

import java.util.function.Function;

import org.danteplanner.backend.shared.failure.Option;

public final class ChosenReadSource {

    private static final ThreadLocal<Option<ReadSourceSelector.Source>> CHOSEN =
            ThreadLocal.withInitial(Option.None::new);

    private ChosenReadSource() {
    }

    public static void record(ReadSourceSelector.Source source) {
        ReadSourceSelector.Source recorded = CHOSEN.get()
                .filter(chosen -> chosen.authority() != ReadSourceSelector.Authority.PRIMARY
                        || source.authority() != ReadSourceSelector.Authority.REPLICA)
                .fold(Function.identity(), () -> source);
        CHOSEN.set(new Option.Some<>(recorded));
    }

    public static Option<ReadSourceSelector.Source> source() {
        return CHOSEN.get();
    }

    public static void clear() {
        CHOSEN.remove();
    }
}
