package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.validation.floor.Admission;
import org.danteplanner.backend.planner.validation.floor.FloorBoundary;
import org.danteplanner.backend.planner.validation.floor.FloorReferenceData;
import org.danteplanner.backend.planner.validation.floor.FloorReferences;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.floor.Stage;
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
    private final GameDataRegistry gameDataRegistry;
    private final FloorRules floorRules;

    public String validate(String content, String category, int version, Stage stage) {
        try {
            return doValidate(content, category, version, stage);
        } catch (PlannerValidationException ex) {
            ex.setFailedContent(content);
            throw ex;
        }
    }

    public String validateFloorRules(String content, String category, Stage stage) {
        try {
            return doValidateFloorRules(content, category, stage);
        } catch (PlannerValidationException ex) {
            ex.setFailedContent(content);
            throw ex;
        }
    }

    public boolean isSameDocument(String content, String stored) {
        if (stored == null) {
            return false;
        }
        try {
            return JsonDocuments.sameDocument(structuralValidator.parseJson(content), structuralValidator.parseJson(stored));
        } catch (PlannerValidationException ex) {
            return false;
        }
    }

    private String doValidateFloorRules(String content, String category, Stage stage) {
        categoryValidator.validateCategory(category);

        ValidationContext context = new ValidationContext(stage);
        JsonNode parsed = structuralValidator.parseJson(content);
        JsonNode root = gameDataRegistry.idMigrations().normalize(parsed);

        admitFloors(root, category, context);

        throwIfRejected(context);
        return root.equals(parsed) ? content : root.toString();
    }

    private String doValidate(String content, String category, int version, Stage stage) {
        if (content == null || content.isBlank()) {
            log.warn("Validation failed: content is null or empty");
            throw ValidationErrors.emptyContent();
        }

        categoryValidator.validateCategory(category);

        ValidationContext context = new ValidationContext(stage);

        JsonNode parsed = structuralValidator.parseJson(content);
        structuralValidator.validateContentSize(parsed);

        if (!parsed.isObject()) {
            log.warn("Validation failed: content is not a JSON object");
            throw ValidationErrors.malformedJson("root element is not an object");
        }

        JsonNode root = gameDataRegistry.idMigrations().normalize(parsed);

        structuralValidator.validateNoUnknownFields(root);
        structuralValidator.validateRequiredFields(root);
        structuralValidator.validateFieldTypes(root, context);
        equipmentValidator.validateEquipmentSinnerIndices(root, context);
        equipmentValidator.validateDeploymentOrder(root, context);
        skillStateValidator.validateSkillEAState(root, context);
        structuralValidator.validateNoteSize(root);

        idReferenceValidator.validateEquipmentIds(root, context);
        idReferenceValidator.validateGiftIds(root, context);
        FloorReferences.check(admitFloors(root, category, context), FloorReferenceData.of(gameDataRegistry))
                .forEach(violation -> rejectAt(violation, context));
        if (startBuffValidator.validateSeasonExists(version, context)) {
            startBuffValidator.validateStartBuffIds(root, version, context);
            startBuffValidator.validateStartGiftIds(root, version, context);
        }

        throwIfRejected(context);
        return root.equals(parsed) ? content : root.toString();
    }

    private FloorBoundary.Parsed admitFloors(JsonNode root, String category, ValidationContext context) {
        FloorBoundary.Parsed parsed = FloorBoundary.parse(root.path("floorSelections"),
                gameDataRegistry.floorRules().layout(MDCategory.fromValue(category)));
        List<Violation> violations = switch (floorRules.admit(parsed, context.stage())) {
            case Admission.Admitted admitted -> admitted.boundaryViolations();
            case Admission.Rejected rejected -> rejected.violations();
        };
        violations.forEach(violation -> rejectAt(violation, context));
        return parsed;
    }

    private static void rejectAt(Violation violation, ValidationContext context) {
        context.reject(violation.path().render(),
                path -> new PlannerValidationException(violation.code().getCode(), violation.message()));
    }

    private static void throwIfRejected(ValidationContext context) {
        List<PlannerValidationException> errors = context.getErrors();
        if (!errors.isEmpty()) {
            throw PlannerValidationException.combined(errors);
        }
    }
}
