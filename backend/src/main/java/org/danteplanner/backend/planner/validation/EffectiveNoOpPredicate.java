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
 * Whether applying a write would leave every persisted field of a content row as it stands.
 *
 * <p>Content is compared as a parsed tree rather than as bytes. MySQL re-serializes a JSON column
 * and the request sanitizer re-renders what a client sends, so a client saving back the document it
 * pulled never resends the stored string; a byte comparison would report that as a change and
 * refuse a write that moves nothing.</p>
 *
 * <p>A document that fails to parse on either side answers false, so a write is never acknowledged
 * on the strength of a comparison that could not be made.</p>
 */
@Component
@RequiredArgsConstructor
public class EffectiveNoOpPredicate {

    private final ObjectMapper objectMapper;

    /**
     * Whether applying {@code carried} to {@code stored} would move no field.
     *
     * @param stored  the content row as it stands
     * @param carried the values the request would apply
     * @return true when every carried field already holds the stored value
     */
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

    /**
     * Keywords are normalized before comparison because the write path normalizes them before
     * assignment, so a client spelling of a set the row already holds moves nothing.
     */
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
