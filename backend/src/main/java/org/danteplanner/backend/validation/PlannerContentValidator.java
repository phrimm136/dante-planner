package org.danteplanner.backend.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.exception.PlannerValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates planner content validation by delegating each concern to a
 * focused sub-validator and threading a single {@link ValidationContext}
 * through the call chain.
 *
 * <p>Behavior is identical to the previous monolith: same error codes, same
 * messages, same call order, same strict-mode semantics, and the same
 * fail-fast vs. accumulate-and-combine behavior.
 */
@Component
@Slf4j
public class PlannerContentValidator {

    private final StructuralValidator structuralValidator;
    private final CategoryValidator categoryValidator;
    private final EquipmentValidator equipmentValidator;
    private final SkillStateValidator skillStateValidator;
    private final IdReferenceValidator idReferenceValidator;
    private final StartBuffValidator startBuffValidator;

    @Autowired
    public PlannerContentValidator(
            StructuralValidator structuralValidator,
            CategoryValidator categoryValidator,
            EquipmentValidator equipmentValidator,
            SkillStateValidator skillStateValidator,
            IdReferenceValidator idReferenceValidator,
            StartBuffValidator startBuffValidator) {
        this.structuralValidator = structuralValidator;
        this.categoryValidator = categoryValidator;
        this.equipmentValidator = equipmentValidator;
        this.skillStateValidator = skillStateValidator;
        this.idReferenceValidator = idReferenceValidator;
        this.startBuffValidator = startBuffValidator;
    }

    public PlannerContentValidator(
            ObjectMapper objectMapper,
            GameDataRegistry gameDataRegistry,
            SinnerIdValidator sinnerIdValidator,
            @Value("${planner.validation.max-content-size}") int maxContentSizeBytes,
            @Value("${planner.validation.max-note-size}") int maxNoteSizeBytes) {
        this(
                new StructuralValidator(objectMapper, maxContentSizeBytes, maxNoteSizeBytes),
                new CategoryValidator(),
                new EquipmentValidator(),
                new SkillStateValidator(),
                new IdReferenceValidator(gameDataRegistry, sinnerIdValidator),
                new StartBuffValidator(gameDataRegistry));
    }

    /**
     * Validate planner content with relaxed rules (for save/draft).
     * Allows empty title and themepack.
     */
    public JsonNode validate(String content, String category) {
        return validate(content, category, false);
    }

    /**
     * Validate planner content.
     *
     * @param content    the content JSON
     * @param category   the planner category
     * @param strictMode if true, requires title and themePackId (for publish)
     */
    public JsonNode validate(String content, String category, boolean strictMode) {
        try {
            return doValidate(content, category, strictMode);
        } catch (PlannerValidationException ex) {
            ex.setFailedContent(content);
            throw ex;
        }
    }

    private JsonNode doValidate(String content, String category, boolean strictMode) {
        if (content == null || content.isBlank()) {
            log.warn("Validation failed: content is null or empty");
            throw ValidationErrors.emptyContent();
        }

        structuralValidator.validateContentSize(content);
        categoryValidator.validateCategory(category);

        ValidationContext context = new ValidationContext(strictMode);

        JsonNode root = structuralValidator.parseJson(content);

        if (!root.isObject()) {
            log.warn("Validation failed: content is not a JSON object");
            throw ValidationErrors.malformedJson("root element is not an object");
        }

        structuralValidator.validateNoUnknownFields(root);
        structuralValidator.validateRequiredFields(root);
        structuralValidator.validateFieldTypes(root, context);
        equipmentValidator.validateEquipmentSinnerIndices(root, context);
        equipmentValidator.validateDeploymentOrder(root, context);
        skillStateValidator.validateSkillEAState(root, context);
        structuralValidator.validateNoteSize(root);

        idReferenceValidator.validateEquipmentIds(root, context);
        idReferenceValidator.validateGiftIds(root, context);
        idReferenceValidator.validateFloorSelectionIds(root, category, context);
        startBuffValidator.validateStartBuffIds(root, context);
        startBuffValidator.validateStartGiftIds(root, context);

        List<PlannerValidationException> errors = context.getErrors();
        if (!errors.isEmpty()) {
            throw PlannerValidationException.combined(errors);
        }

        return root;
    }
}
