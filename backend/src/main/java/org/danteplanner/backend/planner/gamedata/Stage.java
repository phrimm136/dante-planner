package org.danteplanner.backend.planner.gamedata;

import java.util.Arrays;
import java.util.List;

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

    static List<String> tableNames() {
        return Arrays.stream(values()).map(Stage::tableName).toList();
    }
}
