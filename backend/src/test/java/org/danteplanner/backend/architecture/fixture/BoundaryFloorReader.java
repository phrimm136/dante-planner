package org.danteplanner.backend.architecture.fixture;

import org.danteplanner.backend.planner.floor.FloorBoundary;

import com.fasterxml.jackson.databind.JsonNode;

/** Hands the raw floors to the parse boundary and names the field only in a message. */
public class BoundaryFloorReader {

    private static final String FLOOR_SELECTIONS = "floorSelections";

    public FloorBoundary.Parsed parse(JsonNode content) {
        return FloorBoundary.parse(content.path(FLOOR_SELECTIONS), 5);
    }

    public String missingFloorsMessage() {
        return FLOOR_SELECTIONS;
    }
}
