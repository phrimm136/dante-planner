package org.danteplanner.consistency;

import java.util.ArrayList;
import java.util.List;

public final class Shrinker {

    private Shrinker() {
    }

    public static History shrink(History history, Violation target) {
        if (!reports(history, target)) {
            return history;
        }
        List<Operation> kept = history.operations();
        int index = 0;
        while (index < kept.size()) {
            List<Operation> candidate = new ArrayList<>(kept);
            candidate.remove(index);
            if (reports(History.of(candidate), target)) {
                kept = candidate;
                index = 0;
            } else {
                index++;
            }
        }
        return History.of(kept);
    }

    private static boolean reports(History history, Violation target) {
        return ConsistencyChecker.check(history).violations().contains(target);
    }
}
