package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.springframework.stereotype.Component;

import java.util.List;

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

    public JsonNode validate(String content, String category) {
        return validate(content, category, ValidationPolicy.DRAFT);
    }

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

        categoryValidator.validateCategory(category);

        ValidationContext context = new ValidationContext(policy);

        JsonNode root = structuralValidator.parseJson(content);
        structuralValidator.validateContentSize(root);

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
