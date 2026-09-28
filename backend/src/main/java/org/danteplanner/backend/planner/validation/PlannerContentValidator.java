package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.floor.Admission;
import org.danteplanner.backend.planner.floor.FloorBoundary;
import org.danteplanner.backend.planner.floor.FloorRules;
import org.danteplanner.backend.planner.floor.FloorSelection;
import org.danteplanner.backend.planner.floor.ParsedFloor;
import org.danteplanner.backend.planner.floor.Stage;
import org.danteplanner.backend.planner.floor.Violation;
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
        idReferenceValidator.validateFloorIds(admitFloors(root, category, context), context);
        if (startBuffValidator.validateSeasonExists(version, context)) {
            startBuffValidator.validateStartBuffIds(root, version, context);
            startBuffValidator.validateStartGiftIds(root, version, context);
        }

        throwIfRejected(context);
        return root.equals(parsed) ? content : root.toString();
    }

    private List<FloorSelection> admitFloors(JsonNode root, String category, ValidationContext context) {
        MDCategory mdCategory = MDCategory.fromValue(category);
        FloorBoundary.Parsed parsed = FloorBoundary.parse(root.path("floorSelections"),
                gameDataRegistry.floorRules().floorCount(mdCategory));
        List<Violation> violations = switch (floorRules.admit(parsed, mdCategory, context.stage())) {
            case Admission.Admitted admitted -> admitted.boundaryViolations();
            case Admission.Rejected rejected -> rejected.violations();
        };
        violations.forEach(violation -> context.reject(violation.path(),
                path -> new PlannerValidationException(violation.code().getCode(), violation.message())));
        if (!parsed.violations().isEmpty()) {
            return List.of();
        }
        return parsed.floors().stream()
                .<FloorSelection>mapMulti((floor, accepted) -> {
                    if (floor instanceof ParsedFloor.Accepted(FloorSelection selection)) {
                        accepted.accept(selection);
                    }
                })
                .toList();
    }

    private static void throwIfRejected(ValidationContext context) {
        List<PlannerValidationException> errors = context.getErrors();
        if (!errors.isEmpty()) {
            throw PlannerValidationException.combined(errors);
        }
    }
}
