package org.danteplanner.backend.integration.consistency;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

record Response(int status, String body) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    long syncVersion() {
        JsonNode version = json().path("syncVersion");
        if (!version.isIntegralNumber()) {
            throw new IllegalStateException("no integral syncVersion in a " + status + " body: " + body);
        }
        return version.longValue();
    }

    Optional<String> code() {
        try {
            JsonNode code = MAPPER.readTree(body).path("code");
            return code.isString() ? Optional.of(code.stringValue()) : Optional.empty();
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    List<String> plannerIds() {
        List<String> ids = new ArrayList<>();
        for (JsonNode planner : content()) {
            JsonNode id = planner.path("id");
            if (!id.isString()) {
                throw new IllegalStateException("a page entry without a string id: " + planner);
            }
            ids.add(id.stringValue());
        }
        return ids;
    }

    Map<String, Long> plannerVersions() {
        Map<String, Long> versions = new HashMap<>();
        for (JsonNode planner : content()) {
            JsonNode version = planner.path("syncVersion");
            if (!version.isIntegralNumber()) {
                throw new IllegalStateException("a page entry without an integral syncVersion: " + planner);
            }
            versions.put(planner.path("id").stringValue(), version.longValue());
        }
        return versions;
    }

    private JsonNode content() {
        JsonNode content = json().path("content");
        if (!content.isArray()) {
            throw new IllegalStateException("no content array in a " + status + " page body: " + body);
        }
        return content;
    }

    private JsonNode json() {
        try {
            return MAPPER.readTree(body);
        } catch (JacksonException e) {
            throw new IllegalStateException("a " + status + " body is not JSON: " + body, e);
        }
    }
}
