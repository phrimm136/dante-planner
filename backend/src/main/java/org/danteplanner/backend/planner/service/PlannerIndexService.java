package org.danteplanner.backend.planner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.entity.ContentEntityType;
import org.danteplanner.backend.planner.entity.PlannerContentIndex;
import org.danteplanner.backend.planner.repository.PlannerContentIndexRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing the planner content index.
 * Extracts entity IDs from plan content JSON and populates the reverse index table
 * used for searching published plans by their contained entities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerIndexService {

    private final PlannerContentIndexRepository contentIndexRepository;
    private final ObjectMapper objectMapper;

    /**
     * Re-index a planner's content. Deletes existing index entries and inserts new ones.
     * Must run within an existing transaction (caller provides @Transactional).
     *
     * @param plannerId   the planner ID
     * @param contentJson the raw content JSON string
     */
    @Transactional
    public void reindex(UUID plannerId, String contentJson) {
        contentIndexRepository.deleteByPlannerId(plannerId);

        if (contentJson == null || contentJson.isBlank()) {
            log.warn("Empty content for planner {}, index cleared", plannerId);
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(contentJson);
        } catch (Exception e) {
            log.error("Failed to parse content JSON for planner {}: {}", plannerId, e.getMessage());
            return;
        }

        List<PlannerContentIndex> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        extractIdentities(root, plannerId, entries, seen);
        extractEgos(root, plannerId, entries, seen);
        extractEgoGifts(root, plannerId, entries, seen);
        extractThemePacks(root, plannerId, entries, seen);

        if (!entries.isEmpty()) {
            contentIndexRepository.saveAll(entries);
            log.debug("Indexed {} entries for planner {}", entries.size(), plannerId);
        }
    }

    /**
     * Delete all index entries for a planner.
     * Called on unpublish or soft-delete.
     *
     * @param plannerId the planner ID
     */
    @Transactional
    public void deleteIndex(UUID plannerId) {
        contentIndexRepository.deleteByPlannerId(plannerId);
        log.debug("Deleted index entries for planner {}", plannerId);
    }

    /**
     * Extract identity IDs from equipment[*].identity.id
     */
    private void extractIdentities(JsonNode root, UUID plannerId,
                                   List<PlannerContentIndex> entries, Set<String> seen) {
        JsonNode equipment = root.get("equipment");
        if (equipment == null || !equipment.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> sinners = equipment.fields();
        while (sinners.hasNext()) {
            Map.Entry<String, JsonNode> sinnerEntry = sinners.next();
            JsonNode sinnerData = sinnerEntry.getValue();
            if (sinnerData == null || !sinnerData.isObject()) {
                continue;
            }

            JsonNode identity = sinnerData.get("identity");
            if (identity == null || !identity.isObject()) {
                continue;
            }

            JsonNode idNode = identity.get("id");
            if (idNode != null && idNode.isTextual()) {
                addEntry(ContentEntityType.IDENTITY, idNode.asText(), plannerId, entries, seen);
            } else if (idNode != null && idNode.isNumber()) {
                addEntry(ContentEntityType.IDENTITY, idNode.asText(), plannerId, entries, seen);
            }
        }
    }

    /**
     * Extract EGO IDs from equipment[*].egos[*].id
     * egos is a map keyed by ego type (ZAYIN, TETH, HE, WAW, ALEPH)
     */
    private void extractEgos(JsonNode root, UUID plannerId,
                             List<PlannerContentIndex> entries, Set<String> seen) {
        JsonNode equipment = root.get("equipment");
        if (equipment == null || !equipment.isObject()) {
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> sinners = equipment.fields();
        while (sinners.hasNext()) {
            Map.Entry<String, JsonNode> sinnerEntry = sinners.next();
            JsonNode sinnerData = sinnerEntry.getValue();
            if (sinnerData == null || !sinnerData.isObject()) {
                continue;
            }

            JsonNode egos = sinnerData.get("egos");
            if (egos == null || !egos.isObject()) {
                continue;
            }

            Iterator<Map.Entry<String, JsonNode>> egoTypes = egos.fields();
            while (egoTypes.hasNext()) {
                Map.Entry<String, JsonNode> egoEntry = egoTypes.next();
                JsonNode egoData = egoEntry.getValue();
                if (egoData == null || !egoData.isObject()) {
                    continue;
                }

                JsonNode idNode = egoData.get("id");
                if (idNode != null && idNode.isTextual()) {
                    addEntry(ContentEntityType.EGO, idNode.asText(), plannerId, entries, seen);
                } else if (idNode != null && idNode.isNumber()) {
                    addEntry(ContentEntityType.EGO, idNode.asText(), plannerId, entries, seen);
                }
            }
        }
    }

    /**
     * Extract EGO gift IDs from:
     * - selectedGiftIds (array)
     * - observationGiftIds (array)
     * - comprehensiveGiftIds (array)
     * - floorSelections[*].giftIds (array per floor)
     */
    private void extractEgoGifts(JsonNode root, UUID plannerId,
                                 List<PlannerContentIndex> entries, Set<String> seen) {
        extractIdsFromArray(root.get("selectedGiftIds"), ContentEntityType.EGO_GIFT, plannerId, entries, seen);
        extractIdsFromArray(root.get("observationGiftIds"), ContentEntityType.EGO_GIFT, plannerId, entries, seen);
        extractIdsFromArray(root.get("comprehensiveGiftIds"), ContentEntityType.EGO_GIFT, plannerId, entries, seen);

        JsonNode floorSelections = root.get("floorSelections");
        if (floorSelections != null && floorSelections.isArray()) {
            for (JsonNode floor : floorSelections) {
                if (floor != null && floor.isObject()) {
                    extractIdsFromArray(floor.get("giftIds"), ContentEntityType.EGO_GIFT, plannerId, entries, seen);
                }
            }
        }
    }

    /**
     * Extract theme pack IDs from floorSelections[*].themePackId (non-null only)
     */
    private void extractThemePacks(JsonNode root, UUID plannerId,
                                   List<PlannerContentIndex> entries, Set<String> seen) {
        JsonNode floorSelections = root.get("floorSelections");
        if (floorSelections == null || !floorSelections.isArray()) {
            return;
        }

        for (JsonNode floor : floorSelections) {
            if (floor == null || !floor.isObject()) {
                continue;
            }

            JsonNode themePackId = floor.get("themePackId");
            if (themePackId != null && !themePackId.isNull()) {
                String id = themePackId.asText();
                if (!id.isEmpty()) {
                    addEntry(ContentEntityType.THEME_PACK, id, plannerId, entries, seen);
                }
            }
        }
    }

    /**
     * Extract string IDs from a JSON array node.
     */
    private void extractIdsFromArray(JsonNode arrayNode, ContentEntityType type, UUID plannerId,
                                     List<PlannerContentIndex> entries, Set<String> seen) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }

        for (JsonNode element : arrayNode) {
            if (element != null && !element.isNull()) {
                String id = element.asText();
                if (!id.isEmpty()) {
                    addEntry(type, id, plannerId, entries, seen);
                }
            }
        }
    }

    /**
     * Add a content index entry, deduplicating by (type, entityId) within a single planner.
     */
    private void addEntry(ContentEntityType type, String entityId, UUID plannerId,
                          List<PlannerContentIndex> entries, Set<String> seen) {
        String key = type.name() + ":" + entityId;
        if (seen.add(key)) {
            entries.add(new PlannerContentIndex(type, entityId, plannerId));
        }
    }
}
