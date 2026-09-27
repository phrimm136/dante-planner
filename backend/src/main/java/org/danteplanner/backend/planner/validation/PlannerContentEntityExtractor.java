package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;

import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.shared.entity.ContentEntityType;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class PlannerContentEntityExtractor {

    public record EntityRef(ContentEntityType type, int id) {
    }

    private PlannerContentEntityExtractor() {
    }

    public static Set<EntityRef> extract(JsonNode root, MDCategory category) {
        Set<EntityRef> refs = new LinkedHashSet<>();
        if (root == null || !root.isObject()) {
            return refs;
        }
        extractFromEquipment(root, refs);
        addIdsFromArray(root.get("selectedGiftIds"), ContentEntityType.EGO_GIFT, refs);
        addIdsFromArray(root.get("observationGiftIds"), ContentEntityType.EGO_GIFT, refs);
        addIdsFromArray(root.get("comprehensiveGiftIds"), ContentEntityType.EGO_GIFT, refs);
        extractFromFloorSelections(root, category.floorCount(), refs);
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

    private static void extractFromFloorSelections(JsonNode root, int floorCount, Set<EntityRef> refs) {
        JsonNode floorSelections = root.get("floorSelections");
        if (floorSelections == null || !floorSelections.isArray()) {
            return;
        }
        for (int index = 0; index < floorSelections.size() && index < floorCount; index++) {
            JsonNode floor = floorSelections.get(index);
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
            int id = Integer.parseInt(raw);
            refs.add(new EntityRef(type, type == ContentEntityType.EGO_GIFT ? baseGiftId(id) : id));
        } catch (NumberFormatException ignored) {
        }
    }

    /**
     * Content stores an enhanced gift as the enhancement level prefixed onto the four-digit
     * base id, so {@code 19154} and {@code 29154} both denote gift {@code 9154}. An id outside
     * those bands is returned unchanged.
     */
    static int baseGiftId(int id) {
        boolean enhanced = (id >= 19000 && id <= 19999) || (id >= 29000 && id <= 29999);
        return enhanced ? id % 10000 : id;
    }
}
