package org.danteplanner.backend.planner.floor;

public sealed interface Difficulty permits Difficulty.Unset, Difficulty.Set {

    static Difficulty unset() {
        return new Unset();
    }

    static Difficulty of(int value) {
        return new Set(value);
    }

    record Unset() implements Difficulty {
    }

    record Set(int value) implements Difficulty {
    }
}
