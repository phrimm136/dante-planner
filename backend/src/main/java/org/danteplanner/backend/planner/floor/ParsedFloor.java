package org.danteplanner.backend.planner.floor;

public sealed interface ParsedFloor permits ParsedFloor.Accepted, ParsedFloor.Rejected {

    record Accepted(FloorSelection floor) implements ParsedFloor {
    }

    record Rejected(int index, FloorSelection salvage) implements ParsedFloor {
    }
}
