package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.databind.JsonNode;

import org.danteplanner.backend.shared.entity.ContentEntityType;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Pure extraction of content-entity references from planner content JSON.
 * Deliberately framework-free: the runtime filter maintenance and the one-shot
 * migration backfill both call this, so the inverted index is always built by
 * the same code path. All entity ids are integers; non-numeric values are dropped.
 */
public final class PlannerContentEntityExtractor {

    /**
     * A single (type, id) content-entity reference.
     */
    public record EntityRef(ContentEntityType type, int id) {
    }

    private PlannerContentEntityExtractor() {
    }

    /**
     * Extract all content-entity references from a parsed content document:
     * identities from {@code equipment[*].identity.id}, EGOs from
     * {@code equipment[*].egos[*].id}, gifts from the three gift-id arrays and
     * {@code floorSelections[*].giftIds}, theme packs from
     * {@code floorSelections[*].themePackId}. Deduplicated, insertion-ordered.
     */
    public static Set<EntityRef> extract(JsonNode root) {
        Set<EntityRef> refs = new LinkedHashSet<>();
        if (root == null || !root.isObject()) {
            return refs;
        }
        extractFromEquipment(root, refs);
        addIdsFromArray(root.get("selectedGiftIds"), ContentEntityType.EGO_GIFT, refs);
        addIdsFromArray(root.get("observationGiftIds"), ContentEntityType.EGO_GIFT, refs);
        addIdsFromArray(root.get("comprehensiveGiftIds"), ContentEntityType.EGO_GIFT, refs);
        extractFromFloorSelections(root, refs);
        return refs;
    }

    private static void extractFromEquipment(JsonNode root, Set<EntityRef> refs) {
        JsonNode equipment = root.get("equipment");
        if (equipment == null || !equipment.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> sinners = equipment.fields();
        while (sinners.hasNext()) {
            JsonNode sinnerData = sinners.next().getValue();
            if (sinnerData == null || !sinnerData.isObject()) {
                continue;
            }
            JsonNode identity = sinnerData.get("identity");
            if (identity != null && identity.isObject()) {
                addId(identity.get("id"), ContentEntityType.IDENTITY, refs);
            }
            JsonNode egos = sinnerData.get("egos");
            if (egos != null && egos.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> egoTypes = egos.fields();
                while (egoTypes.hasNext()) {
                    JsonNode egoData = egoTypes.next().getValue();
                    if (egoData != null && egoData.isObject()) {
                        addId(egoData.get("id"), ContentEntityType.EGO, refs);
                    }
                }
            }
        }
    }

    private static void extractFromFloorSelections(JsonNode root, Set<EntityRef> refs) {
        JsonNode floorSelections = root.get("floorSelections");
        if (floorSelections == null || !floorSelections.isArray()) {
            return;
        }
        for (JsonNode floor : floorSelections) {
            if (floor == null || !floor.isObject()) {
                continue;
            }
            addIdsFromArray(floor.get("giftIds"), ContentEntityType.EGO_GIFT, refs);
            addId(floor.get("themePackId"), ContentEntityType.THEME_PACK, refs);
        }
    }

    private static void addIdsFromArray(JsonNode arrayNode, ContentEntityType type, Set<EntityRef> refs) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }
        for (JsonNode element : arrayNode) {
            addId(element, type, refs);
        }
    }

    private static void addId(JsonNode idNode, ContentEntityType type, Set<EntityRef> refs) {
        if (idNode == null || idNode.isNull()) {
            return;
        }
        String raw = idNode.asText();
        if (raw.isEmpty()) {
            return;
        }
        try {
            refs.add(new EntityRef(type, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            // entity ids are integers by contract; anything else is not indexable
        }
    }
}
