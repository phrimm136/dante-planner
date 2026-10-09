package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

record Response(int status, String body) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    long syncVersion() {
        JsonNode version = json().path("syncVersion");
        if (!version.isIntegralNumber()) {
            throw new IllegalStateException("no integral syncVersion in a " + status + " body: " + body);
        }
        return version.longValue();
    }

    List<String> plannerIds() {
        JsonNode content = json().path("content");
        if (!content.isArray()) {
            throw new IllegalStateException("no content array in a " + status + " page body: " + body);
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode planner : content) {
            JsonNode id = planner.path("id");
            if (!id.isTextual()) {
                throw new IllegalStateException("a page entry without a string id: " + planner);
            }
            ids.add(id.textValue());
        }
        return ids;
    }

    private JsonNode json() {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a " + status + " body is not JSON: " + body, e);
        }
    }
}
