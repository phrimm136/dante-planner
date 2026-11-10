package org.danteplanner.backend.shared.freshness;

import java.util.Objects;
import java.util.UUID;

public sealed interface FreshnessScope {

    String key();

    record Planner(UUID plannerId) implements FreshnessScope {

        private static final String KEY_PREFIX = "chg:planner:";

        public Planner {
            Objects.requireNonNull(plannerId, "plannerId");
        }

        @Override
        public String key() {
            return KEY_PREFIX + plannerId;
        }
    }

    record User(long userId) implements FreshnessScope {

        private static final String KEY_PREFIX = "chg:user:";

        @Override
        public String key() {
            return KEY_PREFIX + userId;
        }
    }

    record Global() implements FreshnessScope {

        private static final String KEY = "chg:global";

        @Override
        public String key() {
            return KEY;
        }
    }
}
