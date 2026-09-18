package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.danteplanner.backend.planner.entity.PlannerContent;
import org.danteplanner.backend.planner.entity.PlannerKeywords;

import java.util.Objects;
import java.util.Set;

/**
 * MySQL re-serializes a JSON column
 * and the request sanitizer re-renders what a client sends, so a client saving back the document it
 * pulled never resends the stored string.
 */
@Component
@RequiredArgsConstructor
public class EffectiveNoOpPredicate {

    private final ObjectMapper objectMapper;

    public boolean isEffectiveNoOp(PlannerContent stored, CarriedWrite carried) {
        return unchanged(carried.title(), stored.getTitle())
                && unchanged(carried.status(), stored.getStatus())
                && unchanged(carried.category(), stored.getCategory())
                && unchanged(carried.gameContentVersion(), stored.getGameContentVersion())
                && unchanged(carried.contentSchemaVersion(), stored.getContentSchemaVersion())
                && unchanged(carried.deviceId(), stored.getDeviceId())
                && keywordsUnchanged(carried.selectedKeywords(), stored.getSelectedKeywords())
                && contentUnchanged(carried.content(), stored.getContent());
    }

    private static boolean unchanged(Object carried, Object storedValue) {
        return carried == null || Objects.equals(carried, storedValue);
    }

    private static boolean keywordsUnchanged(Set<String> carried, Set<String> storedValue) {
        return carried == null
                || Objects.equals(PlannerKeywords.fromClient(carried).asSet(), storedValue);
    }

    private boolean contentUnchanged(String carried, String storedValue) {
        if (carried == null) {
            return true;
        }
        if (storedValue == null) {
            return false;
        }
        try {
            return objectMapper.readTree(carried).equals(objectMapper.readTree(storedValue));
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
