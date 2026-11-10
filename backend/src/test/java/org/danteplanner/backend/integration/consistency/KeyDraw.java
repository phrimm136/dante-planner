package org.danteplanner.backend.integration.consistency;

import java.util.List;

public sealed interface KeyDraw {

    static KeyDraw share(double share) {
        return new Share(share);
    }

    static KeyDraw index(int index) {
        return new Index(index);
    }

    <T> T from(List<T> pool);

    record Share(double share) implements KeyDraw {

        public Share {
            if (share < 0 || share >= 1) {
                throw new IllegalArgumentException("a key draw is a share in [0, 1), not " + share);
            }
        }

        @Override
        public <T> T from(List<T> pool) {
            if (pool.isEmpty()) {
                throw new IllegalStateException("a key was drawn from an empty planner pool");
            }
            return pool.get((int) (share * pool.size()));
        }
    }

    record Index(int index) implements KeyDraw {

        public Index {
            if (index < 0) {
                throw new IllegalArgumentException("a pool index is not negative, not " + index);
            }
        }

        @Override
        public <T> T from(List<T> pool) {
            if (index >= pool.size()) {
                throw new IllegalStateException("pool index " + index + " is outside a pool of " + pool.size());
            }
            return pool.get(index);
        }
    }
}
