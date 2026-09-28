package org.danteplanner.backend.planner.floor;

import java.math.BigInteger;

public sealed interface Difficulty permits Difficulty.Unset, Difficulty.Set, Difficulty.OutOfRange {

    static Difficulty unset() {
        return new Unset();
    }

    static Difficulty of(int value) {
        return new Set(value);
    }

    static Difficulty outOfRange(BigInteger value) {
        return new OutOfRange(value);
    }

    record Unset() implements Difficulty {
    }

    record Set(int value) implements Difficulty {
    }

    record OutOfRange(BigInteger value) implements Difficulty {
    }
}
