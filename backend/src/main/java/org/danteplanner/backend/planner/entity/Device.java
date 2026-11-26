package org.danteplanner.backend.planner.entity;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public sealed interface Device {

    record Known(UUID id) implements Device {

        public Known {
            Objects.requireNonNull(id, "id");
        }
    }

    record Unknown() implements Device {
    }

    static Device of(@Nullable UUID deviceId) {
        return deviceId == null ? new Unknown() : new Known(deviceId);
    }

    default @Nullable UUID columnValue() {
        return switch (this) {
            case Known(UUID id) -> id;
            case Unknown unknown -> null;
        };
    }
}
