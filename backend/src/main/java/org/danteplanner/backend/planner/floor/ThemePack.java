package org.danteplanner.backend.planner.floor;

public sealed interface ThemePack permits ThemePack.None, ThemePack.Chosen {

    static ThemePack none() {
        return new None();
    }

    static ThemePack chosen(String id) {
        return new Chosen(id);
    }

    record None() implements ThemePack {
    }

    record Chosen(String id) implements ThemePack {

        public Chosen {
            if (id.isEmpty()) {
                throw new IllegalArgumentException("a chosen theme pack id is never empty");
            }
        }
    }
}
