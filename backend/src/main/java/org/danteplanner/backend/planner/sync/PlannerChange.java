package org.danteplanner.backend.planner.sync;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.danteplanner.backend.planner.entity.PlannerContent;

import java.util.Objects;

public record PlannerChange(
        boolean titleChanged,
        boolean statusChanged,
        boolean categoryChanged,
        boolean contentChanged,
        boolean gameContentVersionChanged,
        boolean contentSchemaVersionChanged,
        boolean deviceIdChanged) {

    public static PlannerChange between(PlannerContent stored, CarriedWrite carried, ObjectMapper mapper) {
        return new PlannerChange(
                changed(carried.title(), stored.getTitle()),
                changed(carried.status(), stored.getStatus()),
                changed(carried.category(), stored.getCategory()),
                contentChanged(carried.content(), stored.getContent(), mapper),
                changed(carried.gameContentVersion(), stored.getGameContentVersion()),
                changed(carried.contentSchemaVersion(), stored.getContentSchemaVersion()),
                changed(carried.deviceId(), stored.getDeviceId()));
    }

    public boolean isNone() {
        return !titleChanged && !statusChanged && !categoryChanged && !contentChanged
                && !gameContentVersionChanged && !contentSchemaVersionChanged && !deviceIdChanged;
    }

    public boolean categoryOnly() {
        return categoryChanged && !gameContentVersionChanged && !contentChanged;
    }

    private static boolean changed(Object carried, Object stored) {
        return carried != null && !Objects.equals(carried, stored);
    }

    private static boolean contentChanged(String carried, String stored, ObjectMapper mapper) {
        if (carried == null) {
            return false;
        }
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
