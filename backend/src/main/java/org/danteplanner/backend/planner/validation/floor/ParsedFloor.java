package org.danteplanner.backend.planner.validation.floor;

import java.util.Optional;

sealed interface ParsedFloor permits ParsedFloor.Accepted, ParsedFloor.Rejected {

    Optional<FloorSelection> accepted();

    FloorSelection indexed();

    record Accepted(FloorSelection floor) implements ParsedFloor {

        @Override
        public Optional<FloorSelection> accepted() {
            return Optional.of(floor);
        }

        @Override
        public FloorSelection indexed() {
            return floor;
        }
    }

    record Rejected(FloorSelection salvage) implements ParsedFloor {

        @Override
        public Optional<FloorSelection> accepted() {
            return Optional.empty();
        }

        @Override
        public FloorSelection indexed() {
            return salvage;
        }
    }
}
