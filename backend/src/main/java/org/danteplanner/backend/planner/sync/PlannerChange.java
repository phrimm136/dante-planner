package org.danteplanner.backend.planner.sync;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.planner.entity.PlannerContent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record PlannerChange(Set<Field> fields) {

    public enum Field {
        TITLE,
        STATUS,
        CATEGORY,
        CONTENT,
        GAME_CONTENT_VERSION,
        CONTENT_SCHEMA_VERSION,
        DEVICE_ID
    }

    public enum ContentSource {
        CARRIED,
        STORED
    }

    public static PlannerChange between(PlannerContent stored, CarriedWrite carried, ObjectMapper mapper) {
        Set<Field> fields = EnumSet.allOf(Field.class);
        fields.removeIf(field -> !moved(field, stored, carried, mapper));
        return new PlannerChange(Collections.unmodifiableSet(fields));
    }

    public boolean isNone() {
        return fields.isEmpty();
    }

    public boolean categoryOnly() {
        return fields.contains(Field.CATEGORY)
                && !fields.contains(Field.GAME_CONTENT_VERSION) && !fields.contains(Field.CONTENT);
    }

    public ContentSource contentSource() {
        return categoryOnly() ? ContentSource.STORED : ContentSource.CARRIED;
    }

    private static boolean moved(Field field, PlannerContent stored, CarriedWrite carried, ObjectMapper mapper) {
        return switch (field) {
            case TITLE -> !Objects.equals(carried.title(), stored.getTitle());
            case STATUS -> !Objects.equals(carried.status(), stored.getStatus());
            case CATEGORY -> !Objects.equals(carried.category(), stored.getCategory());
            case CONTENT -> contentChanged(carried.content(), stored.getContent(), mapper);
            case GAME_CONTENT_VERSION ->
                    !Objects.equals(carried.gameContentVersion(), stored.getGameContentVersion());
            case CONTENT_SCHEMA_VERSION ->
                    !Objects.equals(carried.contentSchemaVersion(), stored.getContentSchemaVersion());
            case DEVICE_ID -> !Objects.equals(carried.deviceId(), stored.getDeviceId());
        };
    }

    private static boolean contentChanged(String carried, String stored, ObjectMapper mapper) {
        if (stored == null) {
            return true;
        }
        // MySQL re-serializes the JSON column and the request sanitizer re-renders client content,
        // so a resend of the pulled document never carries the stored string byte for byte.
        try {
            return !JsonDocuments.sameDocument(mapper.readTree(carried), mapper.readTree(stored));
        } catch (JacksonException e) {
            return true;
        }
    }
}
