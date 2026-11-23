package org.danteplanner.fixture.jsontree;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public final class JsonTreeFixture {

    public JsonNode attrsOf(JsonNode node) {
        return node.get("attrs");
    }

    public JsonNode firstOf(JsonNode array) {
        return array.get(0);
    }

    public JsonNode typeOf(ObjectNode node) {
        return node.get("type");
    }

    public JsonNode pathOf(JsonNode node) {
        return node.path("attrs");
    }
}
