package org.danteplanner.backend.integration.consistency;

import java.util.List;

public record KeyDraw(double share) {

    public KeyDraw {
        if (share < 0 || share >= 1) {
            throw new IllegalArgumentException("a key draw is a share in [0, 1), not " + share);
        }
    }

    <T> T from(List<T> pool) {
        if (pool.isEmpty()) {
            throw new IllegalStateException("a key was drawn from an empty planner pool");
        }
        return pool.get((int) (share * pool.size()));
    }
}
