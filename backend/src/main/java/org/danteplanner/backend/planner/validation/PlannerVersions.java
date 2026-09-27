package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PlannerVersions(int schemaVersion, List<Integer> mdAvailableVersions, List<Integer> rrAvailableVersions) {

    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String MD_AVAILABLE_VERSIONS = "mdAvailableVersions";
    private static final String RR_AVAILABLE_VERSIONS = "rrAvailableVersions";
    private static final Set<String> KEYS = Set.of(SCHEMA_VERSION, MD_AVAILABLE_VERSIONS, RR_AVAILABLE_VERSIONS);

    public PlannerVersions {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("'" + SCHEMA_VERSION + "' must be positive, got " + schemaVersion);
        }
        mdAvailableVersions = requireStrictlyAscending(MD_AVAILABLE_VERSIONS, mdAvailableVersions);
        rrAvailableVersions = requireStrictlyAscending(RR_AVAILABLE_VERSIONS, rrAvailableVersions);
    }

    public int mdCurrentVersion() {
        return mdAvailableVersions.get(mdAvailableVersions.size() - 1);
    }

    public static PlannerVersions parse(JsonNode root) {
        if (!root.isObject()) {
            throw new IllegalArgumentException("root must be an object");
        }
        for (Map.Entry<String, JsonNode> field : root.properties()) {
            if (!KEYS.contains(field.getKey())) {
                throw new IllegalArgumentException("unknown key '" + field.getKey() + "'; expected " + KEYS);
            }
        }
        JsonNode schemaVersion = root.path(SCHEMA_VERSION);
        if (!schemaVersion.isInt()) {
            throw new IllegalArgumentException("'" + SCHEMA_VERSION + "' must be an integer");
        }
        return new PlannerVersions(
                schemaVersion.asInt(),
                parseVersionList(root, MD_AVAILABLE_VERSIONS),
                parseVersionList(root, RR_AVAILABLE_VERSIONS));
    }

    private static List<Integer> parseVersionList(JsonNode root, String key) {
        JsonNode node = root.path(key);
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + key + "' must be an array of integers");
        }
        List<Integer> versions = new ArrayList<>();
        for (JsonNode version : node) {
            if (!version.isInt()) {
                throw new IllegalArgumentException("'" + key + "' must hold integers only, got " + version);
            }
            versions.add(version.asInt());
        }
        return versions;
    }

    private static List<Integer> requireStrictlyAscending(String key, List<Integer> versions) {
        if (versions == null || versions.isEmpty()) {
            throw new IllegalArgumentException("'" + key + "' must not be empty");
        }
        List<Integer> copy = List.copyOf(versions);
        for (int i = 1; i < copy.size(); i++) {
            if (copy.get(i) <= copy.get(i - 1)) {
                throw new IllegalArgumentException("'" + key + "' must be strictly ascending, got " + copy);
            }
        }
        return copy;
    }
}
