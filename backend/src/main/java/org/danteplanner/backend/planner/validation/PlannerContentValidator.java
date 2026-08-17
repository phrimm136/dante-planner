package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates planner content validation by delegating each concern to a
 * focused sub-validator and threading a single {@link ValidationContext}
 * through the call chain.
 *
 * <p>Structural failures throw and abort; reference failures accumulate into the
 * context and are combined once at the end.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlannerContentValidator {

    private final StructuralValidator structuralValidator;
    private final CategoryValidator categoryValidator;
    private final EquipmentValidator equipmentValidator;
    private final SkillStateValidator skillStateValidator;
    private final IdReferenceValidator idReferenceValidator;
    private final StartBuffValidator startBuffValidator;

    /**
     * Validate planner content with relaxed rules (for save/draft).
     * Allows empty title and themepack.
     */
    public JsonNode validate(String content, String category) {
        return validate(content, category, ValidationPolicy.DRAFT);
    }

    /**
     * Validate planner content.
     *
     * @param content  the content JSON
     * @param category the planner category
     * @param policy   how completely the document must be filled in; {@link ValidationPolicy#PUBLISH}
     *                 additionally requires every floor to name a resolvable theme pack
     */
    public JsonNode validate(String content, String category, ValidationPolicy policy) {
        try {
            return doValidate(content, category, policy);
        } catch (PlannerValidationException ex) {
            ex.setFailedContent(content);
            throw ex;
        }
    }

    private JsonNode doValidate(String content, String category, ValidationPolicy policy) {
        if (content == null || content.isBlank()) {
            log.warn("Validation failed: content is null or empty");
            throw ValidationErrors.emptyContent();
        }

        structuralValidator.validateContentSize(content);
        categoryValidator.validateCategory(category);

        ValidationContext context = new ValidationContext(policy);

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
