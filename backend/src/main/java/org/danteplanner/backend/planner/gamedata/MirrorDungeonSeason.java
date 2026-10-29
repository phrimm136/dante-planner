package org.danteplanner.backend.planner.gamedata;

import tools.jackson.databind.JsonNode;

public record MirrorDungeonSeason(int version, int identityMaxLevel, String nameLocalizeId) {

    public static final String FILE = "mirrorDungeon.json";

    private static final String VERSION = "version";
    private static final String IDENTITY_MAX_LEVEL = "identityMaxLevel";
    private static final String NAME_LOCALIZE_ID = "nameLocalizeId";

    public MirrorDungeonSeason {
        if (identityMaxLevel < 1) {
            throw new IllegalArgumentException("'" + IDENTITY_MAX_LEVEL + "' must be at least 1, got "
                    + identityMaxLevel);
        }
    }

    public static MirrorDungeonSeason parse(JsonNode root) {
        if (!root.isObject()) {
            throw new IllegalArgumentException("root must be an object");
        }
        JsonNode nameLocalizeId = root.path(NAME_LOCALIZE_ID);
        if (!nameLocalizeId.isString()) {
            throw new IllegalArgumentException("'" + NAME_LOCALIZE_ID + "' must be a string");
        }
        return new MirrorDungeonSeason(integer(root, VERSION), integer(root, IDENTITY_MAX_LEVEL),
                nameLocalizeId.asString());
    }

    private static int integer(JsonNode root, String key) {
        JsonNode node = root.path(key);
        if (!node.isInt()) {
            throw new IllegalArgumentException("'" + key + "' must be an integer");
        }
        return node.asInt();
    }
}
