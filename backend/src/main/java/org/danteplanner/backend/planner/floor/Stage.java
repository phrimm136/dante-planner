package org.danteplanner.backend.planner.floor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum Stage {
    DRAFT("draft"),
    PUBLISH("publish"),
    INDEX("index");

    private final String tableName;

    Stage(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }

    public static Stage fromTableName(String tableName) {
        return Arrays.stream(values())
                .filter(stage -> stage.tableName.equals(tableName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown floor rule stage '" + tableName
                        + "'; expected one of " + tableNames()));
    }

    static void requireTableStages(List<String> tableStages) {
        if (!Set.copyOf(tableStages).equals(Set.copyOf(tableNames())) || tableStages.size() != values().length) {
            throw new IllegalStateException("floor rule table stages " + tableStages + " differ from " + tableNames());
        }
    }

    private static List<String> tableNames() {
        return Arrays.stream(values()).map(Stage::tableName).toList();
    }
}
