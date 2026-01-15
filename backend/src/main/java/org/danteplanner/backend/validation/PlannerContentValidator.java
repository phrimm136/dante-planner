package org.danteplanner.backend.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@Component
@Slf4j
public class PlannerContentValidator {

    // Granular error codes for different validation failures
    private static final String ERROR_EMPTY_CONTENT = "EMPTY_CONTENT";
    private static final String ERROR_SIZE_EXCEEDED = "SIZE_EXCEEDED";
    private static final String ERROR_MALFORMED_JSON = "MALFORMED_JSON";
    private static final String ERROR_MISSING_REQUIRED_FIELD = "MISSING_REQUIRED_FIELD";
    private static final String ERROR_UNKNOWN_FIELD = "UNKNOWN_FIELD";
    private static final String ERROR_INVALID_CATEGORY = "INVALID_CATEGORY";
    private static final String ERROR_INVALID_FIELD_TYPE = "INVALID_FIELD_TYPE";
    private static final String ERROR_INVALID_ID_REFERENCE = "INVALID_ID_REFERENCE";

    // Externalized configuration for size limits
    private final int maxContentSizeBytes;
    private final int maxNoteSizeBytes;

    // Equipment keys are 1-indexed (1-12)
    private static final int MIN_EQUIPMENT_SINNER = 1;
    private static final int MAX_EQUIPMENT_SINNER = 12;

    // DeploymentOrder values are 0-indexed (0-11)
    private static final int MIN_DEPLOYMENT_SINNER = 0;
    private static final int MAX_DEPLOYMENT_SINNER = 11;

    private static final Set<String> VALID_CATEGORIES = Set.of("5F", "10F", "15F");

    // All sinner keys that must be present (2-digit format)
    private static final Set<String> ALL_SINNER_KEYS = Set.of(
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12"
    );

    // Required EGO type for each sinner
    private static final String REQUIRED_EGO_TYPE = "ZAYIN";

    // Valid EGO types (max 5, all unique)
    private static final Set<String> VALID_EGO_TYPES = Set.of(
            "ZAYIN", "TETH", "HE", "WAW", "ALEPH"
    );

    // Valid skill slot keys (0, 1, 2 for S1, S2, S3)
    private static final Set<String> VALID_SKILL_SLOTS = Set.of("0", "1", "2");

    // Skill slots and their expected total
    private static final int SKILL_EA_TOTAL = 6;

    // Start buff constraints
    // ID format: {1|2|3}{00-09} where 1=base, 2=+, 3=++
    private static final int MAX_START_BUFFS = 10;
    private static final int MIN_BUFF_BASE_ID = 0;
    private static final int MAX_BUFF_BASE_ID = 9;

    private static final Set<String> REQUIRED_KEYS = Set.of(
            "selectedKeywords", "equipment",
            "deploymentOrder", "floorSelections", "sectionNotes"
    );

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "selectedKeywords", "selectedBuffIds",
            "selectedGiftKeyword", "selectedGiftIds", "observationGiftIds",
            "comprehensiveGiftIds", "equipment", "deploymentOrder",
            "skillEAState", "floorSelections", "sectionNotes"
    );

    private final ObjectMapper objectMapper;
    private final GameDataRegistry gameDataRegistry;
    private final SinnerIdValidator sinnerIdValidator;

    public PlannerContentValidator(
            ObjectMapper objectMapper,
            GameDataRegistry gameDataRegistry,
            SinnerIdValidator sinnerIdValidator,
            @Value("${planner.validation.max-content-size}") int maxContentSizeBytes,
            @Value("${planner.validation.max-note-size}") int maxNoteSizeBytes) {
        this.objectMapper = objectMapper;
        this.gameDataRegistry = gameDataRegistry;
        this.sinnerIdValidator = sinnerIdValidator;
        this.maxContentSizeBytes = maxContentSizeBytes;
        this.maxNoteSizeBytes = maxNoteSizeBytes;
    }

    /**
     * Validate planner content JSON with category context.
     *
     * @param content  the planner content JSON string
     * @param category the planner category (5F, 10F, 15F) for context-dependent validation
     * @return the parsed JsonNode if validation passes
     * @throws PlannerValidationException if validation fails
     */
    public JsonNode validate(String content, String category) {
        if (content == null || content.isBlank()) {
            log.warn("Validation failed: content is null or empty");
            throw emptyContentError();
        }

        validateContentSize(content);
        validateCategory(category);

        JsonNode root = parseJson(content);

        if (!root.isObject()) {
            log.warn("Validation failed: content is not a JSON object");
            throw malformedJsonError("root element is not an object");
        }

        validateNoUnknownFields(root);
        validateRequiredFields(root);
        validateFieldTypes(root);
        validateEquipmentSinnerIndices(root);
        validateDeploymentOrder(root);
        validateSkillEAState(root);
        validateNoteSize(root);

        // ID existence and consistency validation
        validateEquipmentIds(root);
        validateGiftIds(root);
        validateFloorSelectionIds(root, category);
        validateStartBuffIds(root);
        validateStartGiftIds(root);

        return root;
    }

    // ========================================================================
    // Error Factory Methods
    // ========================================================================

    private PlannerValidationException emptyContentError() {
        return new PlannerValidationException(ERROR_EMPTY_CONTENT, "Content is empty or null");
    }

    private PlannerValidationException sizeExceededError(String context, int actual, int limit) {
        return new PlannerValidationException(ERROR_SIZE_EXCEEDED,
                String.format("%s size %d exceeds limit %d", context, actual, limit));
    }

    private PlannerValidationException malformedJsonError(String details) {
        return new PlannerValidationException(ERROR_MALFORMED_JSON,
                "Malformed JSON: " + details);
    }

    private PlannerValidationException missingRequiredFieldError(Set<String> fields) {
        return new PlannerValidationException(ERROR_MISSING_REQUIRED_FIELD,
                "Missing required fields: " + fields);
    }

    private PlannerValidationException unknownFieldError(Set<String> fields) {
        return new PlannerValidationException(ERROR_UNKNOWN_FIELD,
                "Unknown fields: " + fields);
    }

    private PlannerValidationException invalidCategoryError(String category) {
        return new PlannerValidationException(ERROR_INVALID_CATEGORY,
                "Invalid category: " + category);
    }

    private PlannerValidationException invalidFieldTypeError(String field, String expectedType) {
        return new PlannerValidationException(ERROR_INVALID_FIELD_TYPE,
                String.format("Field '%s' has wrong type, expected %s", field, expectedType));
    }

    private PlannerValidationException invalidIdReferenceError(String context, String id) {
        return new PlannerValidationException(ERROR_INVALID_ID_REFERENCE,
                String.format("%s ID '%s' not found or invalid", context, id));
    }

    /**
     * Generic validation error for cases not covered by specific error codes.
     * Used for structural validation failures (equipment structure, EGO types, skill slots, etc.).
     */
    private PlannerValidationException validationError() {
        return new PlannerValidationException("INVALID_JSON", "Validation failed");
    }

    private void validateContentSize(String content) {
        int size = content.getBytes(StandardCharsets.UTF_8).length;
        if (size > maxContentSizeBytes) {
            log.warn("Validation failed: content size {} exceeds limit {}", size, maxContentSizeBytes);
            throw sizeExceededError("Content", size, maxContentSizeBytes);
        }
    }

    private void validateNoteSize(JsonNode root) {
        JsonNode sectionNotes = root.get("sectionNotes");
        if (sectionNotes == null || !sectionNotes.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : sectionNotes.properties()) {
            String sectionKey = entry.getKey();
            try {
                String noteJson = objectMapper.writeValueAsString(entry.getValue());
                int noteSize = noteJson.getBytes(StandardCharsets.UTF_8).length;

                if (noteSize > maxNoteSizeBytes) {
                    log.warn("Validation failed: note '{}' size {} exceeds limit {}", sectionKey, noteSize, maxNoteSizeBytes);
                    throw sizeExceededError("Note '" + sectionKey + "'", noteSize, maxNoteSizeBytes);
                }
            } catch (JsonProcessingException e) {
                log.warn("Validation failed: cannot serialize note '{}'", sectionKey);
                throw malformedJsonError("cannot serialize note '" + sectionKey + "'");
            }
        }
    }

    private JsonNode parseJson(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            log.warn("Validation failed: malformed JSON - {}", e.getMessage());
            throw malformedJsonError(e.getMessage());
        }
    }

    private void validateNoUnknownFields(JsonNode root) {
        Set<String> unknown = new HashSet<>();
        Iterator<String> fields = root.fieldNames();

        while (fields.hasNext()) {
            String field = fields.next();
            if (!ALLOWED_KEYS.contains(field)) {
                unknown.add(field);
            }
        }

        if (!unknown.isEmpty()) {
            log.warn("Validation failed: unknown fields - {}", unknown);
            throw unknownFieldError(unknown);
        }
    }

    private void validateRequiredFields(JsonNode root) {
        Set<String> missing = new HashSet<>();

        for (String key : REQUIRED_KEYS) {
            if (!root.has(key)) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            log.warn("Validation failed: missing fields - {}", missing);
            throw missingRequiredFieldError(missing);
        }
    }

    private void validateFieldTypes(JsonNode root) {
        validateType(root, "selectedKeywords", JsonNode::isArray, "array");
        validateType(root, "deploymentOrder", JsonNode::isArray, "array");
        validateType(root, "floorSelections", JsonNode::isArray, "array");
        validateType(root, "equipment", JsonNode::isObject, "object");
        validateType(root, "sectionNotes", JsonNode::isObject, "object");

        validateOptionalType(root, "selectedBuffIds", JsonNode::isArray, "array");
        validateOptionalType(root, "selectedGiftIds", JsonNode::isArray, "array");
        validateOptionalType(root, "observationGiftIds", JsonNode::isArray, "array");
        validateOptionalType(root, "comprehensiveGiftIds", JsonNode::isArray, "array");
        validateOptionalType(root, "skillEAState", JsonNode::isObject, "object");

        if (root.has("selectedGiftKeyword")) {
            JsonNode node = root.get("selectedGiftKeyword");
            if (!node.isTextual() && !node.isNull()) {
                log.warn("Validation failed: 'selectedGiftKeyword' wrong type");
                throw validationError();
            }
        }
    }

    private void validateType(JsonNode root, String field, Predicate<JsonNode> check, String expected) {
        JsonNode node = root.get(field);
        if (node != null && !check.test(node)) {
            log.warn("Validation failed: '{}' wrong type (expected {})", field, expected);
            throw validationError();
        }
    }

    private void validateOptionalType(JsonNode root, String field, Predicate<JsonNode> check, String expected) {
        if (root.has(field)) {
            validateType(root, field, check, expected);
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.isBlank()) {
            log.warn("Validation failed: category is null or blank");
            throw invalidCategoryError(category);
        }

        if (!VALID_CATEGORIES.contains(category)) {
            log.warn("Validation failed: invalid category '{}'", category);
            throw invalidCategoryError(category);
        }
    }

    private void validateEquipmentSinnerIndices(JsonNode root) {
        JsonNode equipment = root.get("equipment");
        if (equipment == null || !equipment.isObject()) {
            return;
        }

        Set<String> presentKeys = new HashSet<>();
        Iterator<String> keys = equipment.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                int index = Integer.parseInt(key);
                if (index < MIN_EQUIPMENT_SINNER || index > MAX_EQUIPMENT_SINNER) {
                    log.warn("Validation failed: equipment index '{}' out of range", index);
                    throw validationError();
                }
                // Normalize to 2-digit format for completeness check
                presentKeys.add(String.format("%02d", index));
            } catch (NumberFormatException e) {
                log.warn("Validation failed: equipment key '{}' not an integer", key);
                throw validationError();
            }
        }

        // Check all 12 sinners are present
        Set<String> missingSinners = new HashSet<>(ALL_SINNER_KEYS);
        missingSinners.removeAll(presentKeys);
        if (!missingSinners.isEmpty()) {
            log.warn("Validation failed: missing sinners in equipment - {}", missingSinners);
            throw validationError();
        }

        // Validate each sinner has identity and ZAYIN EGO
        for (String sinnerKey : presentKeys) {
            JsonNode sinnerEquipment = equipment.get(sinnerKey);
            if (sinnerEquipment == null) {
                // Try without leading zero (e.g., "1" instead of "01")
                sinnerEquipment = equipment.get(String.valueOf(Integer.parseInt(sinnerKey)));
            }
            if (sinnerEquipment == null || !sinnerEquipment.isObject()) {
                log.warn("Validation failed: equipment[{}] is not an object", sinnerKey);
                throw validationError();
            }

            validateSinnerHasIdentity(sinnerKey, sinnerEquipment);
            validateSinnerHasZayinEgo(sinnerKey, sinnerEquipment);
        }
    }

    private void validateSinnerHasIdentity(String sinnerKey, JsonNode sinnerEquipment) {
        JsonNode identity = sinnerEquipment.get("identity");
        if (identity == null || !identity.isObject()) {
            log.warn("Validation failed: equipment[{}] missing identity", sinnerKey);
            throw validationError();
        }

        JsonNode idNode = identity.get("id");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            log.warn("Validation failed: equipment[{}].identity missing id", sinnerKey);
            throw validationError();
        }
    }

    private void validateSinnerHasZayinEgo(String sinnerKey, JsonNode sinnerEquipment) {
        JsonNode egos = sinnerEquipment.get("egos");
        if (egos == null || !egos.isObject()) {
            log.warn("Validation failed: equipment[{}] missing egos", sinnerKey);
            throw validationError();
        }

        // Validate EGO types: max 5, unique, from valid set
        validateEgoTypes(sinnerKey, egos);

        // Check ZAYIN EGO is required
        JsonNode zayinEgo = egos.get(REQUIRED_EGO_TYPE);
        if (zayinEgo == null || !zayinEgo.isObject()) {
            log.warn("Validation failed: equipment[{}] missing {} EGO", sinnerKey, REQUIRED_EGO_TYPE);
            throw validationError();
        }

        JsonNode idNode = zayinEgo.get("id");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            log.warn("Validation failed: equipment[{}].egos.{} missing id", sinnerKey, REQUIRED_EGO_TYPE);
            throw validationError();
        }
    }

    private void validateEgoTypes(String sinnerKey, JsonNode egos) {
        Set<String> seenTypes = new HashSet<>();
        Iterator<String> egoKeys = egos.fieldNames();

        while (egoKeys.hasNext()) {
            String egoType = egoKeys.next();

            // Check valid EGO type
            if (!VALID_EGO_TYPES.contains(egoType)) {
                log.warn("Validation failed: equipment[{}].egos has invalid type '{}'", sinnerKey, egoType);
                throw validationError();
            }

            // Check for duplicates
            if (!seenTypes.add(egoType)) {
                log.warn("Validation failed: equipment[{}].egos has duplicate type '{}'", sinnerKey, egoType);
                throw validationError();
            }
        }

        // Max 5 EGO types
        if (seenTypes.size() > VALID_EGO_TYPES.size()) {
            log.warn("Validation failed: equipment[{}].egos has more than {} EGO types", sinnerKey, VALID_EGO_TYPES.size());
            throw validationError();
        }
    }

    private void validateDeploymentOrder(JsonNode root) {
        JsonNode order = root.get("deploymentOrder");
        if (order == null || !order.isArray()) {
            return;
        }

        for (int i = 0; i < order.size(); i++) {
            JsonNode node = order.get(i);
            if (!node.isNumber()) {
                log.warn("Validation failed: deploymentOrder[{}] not a number", i);
                throw validationError();
            }

            int index = node.asInt();
            if (index < MIN_DEPLOYMENT_SINNER || index > MAX_DEPLOYMENT_SINNER) {
                log.warn("Validation failed: deploymentOrder[{}]={} out of range", i, index);
                throw validationError();
            }
        }
    }

    private void validateSkillEAState(JsonNode root) {
        JsonNode skillEAState = root.get("skillEAState");
        if (skillEAState == null) {
            // skillEAState is optional
            return;
        }

        if (!skillEAState.isObject()) {
            log.warn("Validation failed: skillEAState is not an object");
            throw validationError();
        }

        // Collect present sinner keys
        Set<String> presentKeys = new HashSet<>();
        Iterator<String> keys = skillEAState.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                int index = Integer.parseInt(key);
                if (index < MIN_EQUIPMENT_SINNER || index > MAX_EQUIPMENT_SINNER) {
                    log.warn("Validation failed: skillEAState key '{}' out of range", key);
                    throw validationError();
                }
                presentKeys.add(String.format("%02d", index));
            } catch (NumberFormatException e) {
                log.warn("Validation failed: skillEAState key '{}' not an integer", key);
                throw validationError();
            }
        }

        // Check all 12 sinners are present
        Set<String> missingSinners = new HashSet<>(ALL_SINNER_KEYS);
        missingSinners.removeAll(presentKeys);
        if (!missingSinners.isEmpty()) {
            log.warn("Validation failed: missing sinners in skillEAState - {}", missingSinners);
            throw validationError();
        }

        // Validate each sinner's skill slots sum to SKILL_EA_TOTAL
        for (String sinnerKey : presentKeys) {
            JsonNode sinnerSkills = skillEAState.get(sinnerKey);
            if (sinnerSkills == null) {
                sinnerSkills = skillEAState.get(String.valueOf(Integer.parseInt(sinnerKey)));
            }
            if (sinnerSkills == null || !sinnerSkills.isObject()) {
                log.warn("Validation failed: skillEAState[{}] is not an object", sinnerKey);
                throw validationError();
            }

            // Validate skill slot keys and calculate total
            Set<String> seenSlots = new HashSet<>();
            int total = 0;
            Iterator<String> slotKeys = sinnerSkills.fieldNames();
            while (slotKeys.hasNext()) {
                String slotKey = slotKeys.next();

                // Check valid skill slot key (0, 1, 2)
                if (!VALID_SKILL_SLOTS.contains(slotKey)) {
                    log.warn("Validation failed: skillEAState[{}] has invalid slot key '{}'", sinnerKey, slotKey);
                    throw validationError();
                }

                // Check for duplicates
                if (!seenSlots.add(slotKey)) {
                    log.warn("Validation failed: skillEAState[{}] has duplicate slot key '{}'", sinnerKey, slotKey);
                    throw validationError();
                }

                JsonNode slotValue = sinnerSkills.get(slotKey);
                if (!slotValue.isNumber()) {
                    log.warn("Validation failed: skillEAState[{}][{}] is not a number", sinnerKey, slotKey);
                    throw validationError();
                }
                total += slotValue.asInt();
            }

            if (total != SKILL_EA_TOTAL) {
                log.warn("Validation failed: skillEAState[{}] total {} != {}", sinnerKey, total, SKILL_EA_TOTAL);
                throw validationError();
            }
        }
    }

    // ========================================================================
    // ID Existence and Sinner-ID Consistency Validation
    // ========================================================================

    private void validateEquipmentIds(JsonNode root) {
        JsonNode equipment = root.get("equipment");
        if (equipment == null || !equipment.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> entry : equipment.properties()) {
            String sinnerKey = entry.getKey();
            JsonNode sinnerEquipment = entry.getValue();

            validateIdentityId(sinnerKey, sinnerEquipment);
            validateEgoIds(sinnerKey, sinnerEquipment);
        }
    }

    private void validateIdentityId(String sinnerKey, JsonNode sinnerEquipment) {
        JsonNode identity = sinnerEquipment.get("identity");
        if (identity == null || !identity.isObject()) {
            return;
        }

        JsonNode idNode = identity.get("id");
        if (idNode == null || !idNode.isTextual()) {
            return;
        }

        String identityId = idNode.asText();

        // Check ID exists in game data
        if (!gameDataRegistry.hasIdentity(identityId)) {
            log.warn("Validation failed: identity ID '{}' not found in game data", identityId);
            throw validationError();
        }

        // Check sinner-ID consistency
        if (!sinnerIdValidator.validateMatch(sinnerKey, identityId)) {
            log.warn("Validation failed: identity ID '{}' does not match sinner key '{}'", identityId, sinnerKey);
            throw validationError();
        }
    }

    private void validateEgoIds(String sinnerKey, JsonNode sinnerEquipment) {
        JsonNode egos = sinnerEquipment.get("egos");
        if (egos == null || !egos.isObject()) {
            return;
        }

        for (Map.Entry<String, JsonNode> egoEntry : egos.properties()) {
            JsonNode ego = egoEntry.getValue();
            if (ego == null || !ego.isObject()) {
                continue;
            }

            JsonNode idNode = ego.get("id");
            if (idNode == null || !idNode.isTextual()) {
                continue;
            }

            String egoId = idNode.asText();

            // Check ID exists in game data
            if (!gameDataRegistry.hasEgo(egoId)) {
                log.warn("Validation failed: EGO ID '{}' not found in game data", egoId);
                throw validationError();
            }

            // Check sinner-ID consistency
            if (!sinnerIdValidator.validateMatch(sinnerKey, egoId)) {
                log.warn("Validation failed: EGO ID '{}' does not match sinner key '{}'", egoId, sinnerKey);
                throw validationError();
            }
        }
    }

    private void validateGiftIds(JsonNode root) {
        validateGiftIdArray(root, "selectedGiftIds");
        validateGiftIdArray(root, "observationGiftIds");
        validateGiftIdArray(root, "comprehensiveGiftIds");
    }

    private void validateGiftIdArray(JsonNode root, String fieldName) {
        JsonNode array = root.get(fieldName);
        if (array == null || !array.isArray()) {
            return;
        }

        Set<String> seenGiftIds = new HashSet<>();

        for (int i = 0; i < array.size(); i++) {
            JsonNode node = array.get(i);

            // Strict type check: all elements must be strings
            if (!node.isTextual()) {
                log.warn("Validation failed: {}[{}] is not a string", fieldName, i);
                throw validationError();
            }

            String giftId = node.asText();

            // Check for duplicates
            if (!seenGiftIds.add(giftId)) {
                log.warn("Validation failed: {}[{}] has duplicate gift ID '{}'", fieldName, i, giftId);
                throw validationError();
            }

            // Check if gift exists in game data
            if (!gameDataRegistry.hasEgoGift(giftId)) {
                log.warn("Validation failed: {}[{}] gift ID '{}' not found in game data", fieldName, i, giftId);
                throw validationError();
            }
        }
    }

    private void validateFloorSelectionIds(JsonNode root, String category) {
        JsonNode floorSelections = root.get("floorSelections");
        if (floorSelections == null || !floorSelections.isArray()) {
            return;
        }

        // Determine floor count from category to validate only active floors
        int floorCount = switch (category) {
            case "5F" -> 5;
            case "10F" -> 10;
            case "15F" -> 15;
            default -> 15;
        };

        for (int i = 0; i < floorSelections.size(); i++) {
            JsonNode floor = floorSelections.get(i);
            if (!floor.isObject()) {
                continue;
            }

            // Only validate active floors based on category
            if (i >= floorCount) {
                continue;
            }

            // Validate themePackId
            JsonNode themePackNode = floor.get("themePackId");

            // Rule 1: Theme pack is REQUIRED for all active floors
            if (themePackNode == null || themePackNode.isNull() || !themePackNode.isTextual()) {
                log.warn("Validation failed: floorSelections[{}] must have a themePackId", i);
                throw validationError();
            }

            String themePackId = themePackNode.asText();

            // Check if theme pack exists in game data
            if (!gameDataRegistry.hasThemePack(themePackId)) {
                log.warn("Validation failed: floorSelections[{}] themePackId '{}' not found", i, themePackId);
                throw validationError();
            }

            // Rule 2: Progressive prerequisite - floor N requires floor N-1 to have theme pack
            if (i > 0) {
                JsonNode previousFloor = floorSelections.get(i - 1);
                if (previousFloor.isObject()) {
                    JsonNode previousThemePackNode = previousFloor.get("themePackId");
                    if (previousThemePackNode == null || previousThemePackNode.isNull() || !previousThemePackNode.isTextual()) {
                        log.warn("Validation failed: floorSelections[{}] cannot have themePackId because floor {} is missing one", i, i - 1);
                        throw validationError();
                    }
                }
            }

            // Validate giftIds in floor selection
            JsonNode giftIds = floor.get("giftIds");
            if (giftIds != null && giftIds.isArray()) {
                Set<String> seenFloorGiftIds = new HashSet<>();

                for (int j = 0; j < giftIds.size(); j++) {
                    JsonNode giftNode = giftIds.get(j);

                    // Strict type check: all elements must be strings
                    if (!giftNode.isTextual()) {
                        log.warn("Validation failed: floorSelections[{}].giftIds[{}] is not a string", i, j);
                        throw validationError();
                    }

                    String giftId = giftNode.asText();

                    // Check for duplicates within this floor
                    if (!seenFloorGiftIds.add(giftId)) {
                        log.warn("Validation failed: floorSelections[{}].giftIds has duplicate '{}'", i, giftId);
                        throw validationError();
                    }

                    // Check if gift exists in game data
                    if (!gameDataRegistry.hasEgoGift(giftId)) {
                        log.warn("Validation failed: floorSelections[{}].giftIds[{}] '{}' not found", i, j, giftId);
                        throw validationError();
                    }
                }
            }
        }
    }

    /**
     * Validate start buff IDs.
     *
     * <p>Rules:
     * <ul>
     *   <li>Max 10 buffs</li>
     *   <li>Each ID must exist in game data</li>
     *   <li>ID format: {1|2|3}{00-09} (100-109, 200-209, 300-309)</li>
     *   <li>No duplicate base IDs (e.g., can't have both 100 and 200)</li>
     *   <li>Empty array is allowed</li>
     * </ul>
     */
    private void validateStartBuffIds(JsonNode root) {
        JsonNode buffIds = root.get("selectedBuffIds");
        if (buffIds == null || !buffIds.isArray()) {
            // selectedBuffIds is optional
            return;
        }

        // Check max count
        if (buffIds.size() > MAX_START_BUFFS) {
            log.warn("Validation failed: selectedBuffIds has {} items, max is {}",
                    buffIds.size(), MAX_START_BUFFS);
            throw validationError();
        }

        // Track base IDs to detect duplicates with different enhancements
        Set<Integer> seenBaseIds = new HashSet<>();

        for (int i = 0; i < buffIds.size(); i++) {
            JsonNode node = buffIds.get(i);
            if (!node.isNumber()) {
                log.warn("Validation failed: selectedBuffIds[{}] is not a number", i);
                throw validationError();
            }

            int buffId = node.asInt();

            // Check ID exists in game data
            if (!gameDataRegistry.hasStartBuff(String.valueOf(buffId))) {
                log.warn("Validation failed: selectedBuffIds[{}] buff ID '{}' not found in game data", i, buffId);
                throw validationError();
            }

            // Extract base ID (00-09 part)
            int baseId = buffId % 100;
            if (baseId < MIN_BUFF_BASE_ID || baseId > MAX_BUFF_BASE_ID) {
                log.warn("Validation failed: selectedBuffIds[{}] buff ID '{}' has invalid base ID '{}'",
                        i, buffId, baseId);
                throw validationError();
            }

            // Check for duplicate base IDs (different enhancements of same buff)
            if (!seenBaseIds.add(baseId)) {
                log.warn("Validation failed: selectedBuffIds has duplicate base ID '{}' (buff ID '{}')",
                        baseId, buffId);
                throw validationError();
            }
        }
    }

    /**
     * Validate start gift selection.
     *
     * <p>Rules:
     * <ul>
     *   <li>If selectedGiftKeyword is null/absent, selectedGiftIds must be empty or absent</li>
     *   <li>If selectedGiftKeyword is present, it must be a valid keyword</li>
     *   <li>All selectedGiftIds must belong to the selected keyword's pool</li>
     *   <li>No duplicate gift IDs allowed</li>
     * </ul>
     */
    private void validateStartGiftIds(JsonNode root) {
        JsonNode keywordNode = root.get("selectedGiftKeyword");
        JsonNode giftIdsNode = root.get("selectedGiftIds");

        // Determine if keyword is effectively null/absent
        boolean hasKeyword = keywordNode != null && !keywordNode.isNull() && keywordNode.isTextual();
        if (!hasKeyword) {
            // If no keyword, selectedGiftIds must be empty or absent
            if (giftIdsNode != null && giftIdsNode.isArray() && giftIdsNode.size() > 0) {
                log.warn("Validation failed: selectedGiftIds has items but selectedGiftKeyword is null");
                throw validationError();
            }
            return;
        }

        String keyword = keywordNode.asText();

        // Keyword is present, validate it exists
        if (!gameDataRegistry.hasStartGiftKeyword(keyword)) {
            log.warn("Validation failed: selectedGiftKeyword '{}' is not a valid keyword", keyword);
            throw validationError();
        }

        // Get the pool for this keyword
        Set<String> pool = gameDataRegistry.getStartGiftPool(keyword);

        // Validate each gift ID belongs to the keyword's pool and check for duplicates
        if (giftIdsNode != null && giftIdsNode.isArray()) {
            Set<String> seenGiftIds = new HashSet<>();

            for (int i = 0; i < giftIdsNode.size(); i++) {
                JsonNode node = giftIdsNode.get(i);
                if (!node.isTextual()) {
                    log.warn("Validation failed: selectedGiftIds[{}] is not a string", i);
                    throw validationError();
                }

                String giftId = node.asText();

                // Check for duplicates
                if (!seenGiftIds.add(giftId)) {
                    log.warn("Validation failed: selectedGiftIds has duplicate gift ID '{}'", giftId);
                    throw validationError();
                }

                // Check gift belongs to keyword pool
                if (!pool.contains(giftId)) {
                    log.warn("Validation failed: selectedGiftIds[{}] gift '{}' not in keyword '{}' pool",
                            i, giftId, keyword);
                    throw validationError();
                }
            }
        }
    }
}
