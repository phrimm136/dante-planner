package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.floor.Admission;
import org.danteplanner.backend.planner.floor.FloorBoundary;
import org.danteplanner.backend.planner.floor.FloorRules;
import org.danteplanner.backend.planner.floor.FloorSelection;
import org.danteplanner.backend.planner.floor.Stage;
import org.danteplanner.backend.planner.floor.ThemePack;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PlannerContentEntityExtractor {

    public record EntityRef(ContentEntityType type, int id) {
    }

    private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]+$");

    private final FloorRules floorRules;
    private final GameDataRegistry gameDataRegistry;

    public Set<EntityRef> extract(JsonNode root, MDCategory category) {
        Set<EntityRef> refs = new LinkedHashSet<>();
        if (root == null || !root.isObject()) {
            return refs;
        }
        extractFromEquipment(root, refs);
        addIdsFromArray(root.get("selectedGiftIds"), ContentEntityType.EGO_GIFT, refs);
        addIdsFromArray(root.get("observationGiftIds"), ContentEntityType.EGO_GIFT, refs);
        addIdsFromArray(root.get("comprehensiveGiftIds"), ContentEntityType.EGO_GIFT, refs);
        admittedFloors(root, category).forEach(floor -> extractFromFloor(floor, refs));
        return refs;
    }

    private List<FloorSelection> admittedFloors(JsonNode root, MDCategory category) {
        FloorBoundary.Parsed parsed = FloorBoundary.parse(root.path("floorSelections"),
                gameDataRegistry.floorRules().floorCount(category));
        return switch (floorRules.admit(parsed, category, Stage.INDEX)) {
            case Admission.Admitted admitted -> admitted.floors();
            case Admission.Rejected rejected -> throw new IllegalStateException(
                    "the index stage runs no floor rule, yet admission was rejected: " + rejected.violations());
        };
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

    private static void extractFromFloor(FloorSelection floor, Set<EntityRef> refs) {
        floor.giftIds().forEach(giftId -> addId(giftId, ContentEntityType.EGO_GIFT, refs));
        if (floor.themePack() instanceof ThemePack.Chosen(String themePackId)) {
            addId(themePackId, ContentEntityType.THEME_PACK, refs);
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
        addId(idNode.asText(), type, refs);
    }

    private static void addId(String raw, ContentEntityType type, Set<EntityRef> refs) {
        if (!DIGITS_ONLY.matcher(raw).matches()) {
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
