package org.danteplanner.backend.entity;

/**
 * Enum representing the type of entity indexed in planner_content_index.
 * Maps to the MySQL ENUM('IDENTITY', 'EGO', 'EGO_GIFT', 'THEME_PACK').
 */
public enum ContentEntityType {
    IDENTITY,
    EGO,
    EGO_GIFT,
    THEME_PACK
}
