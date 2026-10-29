package org.danteplanner.backend.architecture.fixture;

import tools.jackson.databind.JsonNode;

/** Reads a floor's theme pack straight from the stored JSON, bypassing the floor module. */
public class RawFloorReader {

    public String firstThemePack(JsonNode content) {
        return content.path("floorSelections").path(0).path("themePackId").asString();
    }
}
