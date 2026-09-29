package org.danteplanner.backend.planner.validation.floor;

import java.util.Optional;

public sealed interface ThemePack permits ThemePack.None, ThemePack.Chosen {

    static ThemePack none() {
        return new None();
    }

    static ThemePack chosen(String id) {
        return new Chosen(id);
    }

    Optional<String> chosenId();

    record None() implements ThemePack {

        @Override
        public Optional<String> chosenId() {
            return Optional.empty();
        }
    }

    record Chosen(String id) implements ThemePack {

        public Chosen {
            if (id.isEmpty()) {
                throw new IllegalArgumentException("a chosen theme pack id is never empty");
            }
        }

        @Override
        public Optional<String> chosenId() {
            return Optional.of(id);
        }
    }
}
