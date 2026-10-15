package org.danteplanner.backend.shared.freshness;

import java.util.Objects;
import java.util.UUID;

public sealed interface FreshnessScope {

    String key();

    record Entity(UUID plannerId) implements FreshnessScope {

        private static final String KEY_PREFIX = "chg:planner:";

        public Entity {
            Objects.requireNonNull(plannerId, "plannerId");
        }

        @Override
        public String key() {
            return KEY_PREFIX + plannerId;
        }
    }

    record OwnerList(long userId) implements FreshnessScope {

        private static final String KEY_PREFIX = "chg:owner:";

        @Override
        public String key() {
            return KEY_PREFIX + userId;
        }
    }

    record Author(long userId) implements FreshnessScope {

        private static final String KEY_PREFIX = "chg:author:";

        @Override
        public String key() {
            return KEY_PREFIX + userId;
        }
    }

    record PublicCatalog() implements FreshnessScope {

        private static final String KEY = "chg:catalog";

        @Override
        public String key() {
            return KEY;
        }
    }
}
