package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.validation.ego.EgoRules;
import org.danteplanner.backend.planner.validation.egogift.EgoGiftRules;
import org.danteplanner.backend.planner.validation.floor.Admission;
import org.danteplanner.backend.planner.validation.floor.FloorBoundary;
import org.danteplanner.backend.planner.validation.floor.FloorReferenceData;
import org.danteplanner.backend.planner.validation.floor.FloorReferences;
import org.danteplanner.backend.planner.validation.floor.FloorRules;
import org.danteplanner.backend.planner.validation.floor.Stage;
import org.danteplanner.backend.planner.validation.identity.IdentityRules;
import org.danteplanner.backend.planner.validation.sinner.DeploymentOrder;
import org.danteplanner.backend.planner.validation.sinner.SinnerIdValidator;
import org.danteplanner.backend.planner.validation.sinner.SinnerKeys;
import org.danteplanner.backend.planner.validation.skill.SkillRules;
import org.danteplanner.backend.planner.validation.startbuff.StartBuffRules;
import org.danteplanner.backend.planner.validation.startgift.StartGiftRules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@Slf4j
public class PlannerContentValidator {

    private static final String EQUIPMENT = "equipment";
    private static final String SKILL_EA_STATE = "skillEAState";
    private static final String DEPLOYMENT_ORDER = "deploymentOrder";
    private static final String OBSERVATION_GIFT_IDS = "observationGiftIds";
    private static final String COMPREHENSIVE_GIFT_IDS = "comprehensiveGiftIds";
    private static final String FLOOR_SELECTIONS = "floorSelections";
    private static final String SELECTED_BUFF_IDS = "selectedBuffIds";
    private static final String SELECTED_GIFT_KEYWORD = "selectedGiftKeyword";
    private static final String SELECTED_GIFT_IDS = "selectedGiftIds";

    private final ObjectMapper objectMapper;
    private final DocumentChecks document;
    private final GameDataRegistry gameDataRegistry;
    private final RegistryReferences references;
    private final FloorRules floorRules;

    public PlannerContentValidator(
            ObjectMapper objectMapper,
            @Value("${planner.validation.max-content-size}") int maxContentSizeBytes,
            @Value("${planner.validation.max-note-size}") int maxNoteSizeBytes,
            GameDataRegistry gameDataRegistry,
            SinnerIdValidator sinnerIdValidator,
            FloorRules floorRules) {
        this.objectMapper = objectMapper;
        this.document = new DocumentChecks(maxContentSizeBytes, maxNoteSizeBytes);
        this.gameDataRegistry = gameDataRegistry;
        this.references = new RegistryReferences(gameDataRegistry, sinnerIdValidator);
        this.floorRules = floorRules;
    }

    public PlannerDocument parse(String raw, MDCategory category, Season season, Stage stage) {
        return accepted(check(raw, category, season, stage), raw);
    }

    public PlannerDocument reparseForCategory(String stored, MDCategory category, Stage stage) {
        return accepted(checkFloorRules(stored, category, stage), stored);
    }

    public Season season(int version) {
        if (!gameDataRegistry.hasSeason(version)) {
            throw refusal(new ContentVerdict.Refused(unknownVersion(version)), null);
        }
        return new Season(gameDataRegistry, version);
    }

    public Stage stageOf(Planner planner) {
        return planner.isPublished() ? Stage.PUBLISH : Stage.DRAFT;
    }

    private static PlannerDocument accepted(ContentVerdict verdict, String content) {
        return switch (verdict) {
            case ContentVerdict.Accepted accepted -> accepted.document();
            case ContentVerdict.Refused refused -> throw refusal(refused, content);
            case ContentVerdict.Rejected rejected -> {
                rejected.violations().forEach(violation ->
                        log.warn("Validation failed at {}: {}", violation.path().render(), violation.message()));
                throw PlannerValidationException.rejecting(rejected, content);
            }
        };
    }

    private static PlannerValidationException refusal(ContentVerdict.Refused refused, String content) {
        log.warn("Validation failed: {}", refused.violation().message());
        return PlannerValidationException.refusing(refused, content);
    }

    private ContentVerdict check(String content, MDCategory category, Season season, Stage stage) {
        return DocumentChecks.emptiness(content)
                .<ContentVerdict>map(ContentVerdict.Refused::new)
                .orElseGet(() -> switch (ContentJson.parse(objectMapper, content)) {
                    case ContentJson.Malformed malformed ->
                            new ContentVerdict.Refused(DocumentChecks.malformed(malformed.detail()));
                    case ContentJson.Tree tree -> checkParsed(content, tree.root(), category, season, stage);
                });
    }

    private ContentVerdict checkParsed(String content, JsonNode parsed, MDCategory category, Season season,
                                       Stage stage) {
        return document.contentSize(parsed)
                .or(() -> DocumentChecks.objectRoot(parsed))
                .<ContentVerdict>map(ContentVerdict.Refused::new)
                .orElseGet(() -> checkDocument(content, parsed, gameDataRegistry.idMigrations().normalize(parsed),
                        category, season, stage));
    }

    private ContentVerdict checkDocument(String content, JsonNode parsed, JsonNode root, MDCategory category,
                                         Season season, Stage stage) {
        return DocumentChecks.unknownFields(root)
                .or(() -> DocumentChecks.requiredFields(root))
                .or(() -> document.noteSize(root))
                .<ContentVerdict>map(ContentVerdict.Refused::new)
                .orElseGet(() -> {
                    List<Violation> violations = contentViolations(root, category, season, stage);
                    return violations.isEmpty()
                            ? new ContentVerdict.Accepted(normalized(content, parsed, root))
                            : new ContentVerdict.Rejected(violations);
                });
    }

    private ContentVerdict checkFloorRules(String content, MDCategory category, Stage stage) {
        return switch (ContentJson.parse(objectMapper, content)) {
            case ContentJson.Malformed malformed ->
                    new ContentVerdict.Refused(DocumentChecks.malformed(malformed.detail()));
            case ContentJson.Tree tree -> checkFloors(content, tree.root(), category, stage);
        };
    }

    private ContentVerdict checkFloors(String content, JsonNode parsed, MDCategory category, Stage stage) {
        JsonNode root = gameDataRegistry.idMigrations().normalize(parsed);
        List<Violation> violations = admission(parseFloors(root, category), stage);
        return violations.isEmpty()
                ? new ContentVerdict.Accepted(normalized(content, parsed, root))
                : new ContentVerdict.Rejected(violations);
    }

    private List<Violation> contentViolations(JsonNode root, MDCategory category, Season season, Stage stage) {
        SinnerKeys.Sinners equipment = SinnerKeys.parse(root.path(EQUIPMENT), ContentPath.of(EQUIPMENT));
        SinnerKeys.Sinners skillEAState = SinnerKeys.parse(root.path(SKILL_EA_STATE), ContentPath.of(SKILL_EA_STATE));
        FloorBoundary.Parsed floors = parseFloors(root, category);
        return Stream.of(
                        DocumentChecks.fieldKinds(root),
                        DocumentChecks.selectedKeywords(root, stage),
                        equipment.violations(),
                        IdentityRules.check(equipment.entries(), references),
                        EgoRules.check(equipment.entries(), references),
                        DeploymentOrder.check(root.path(DEPLOYMENT_ORDER)),
                        skillEAState.violations(),
                        SkillRules.check(skillEAState.entries()),
                        EgoGiftRules.check(root.path(OBSERVATION_GIFT_IDS), root.path(COMPREHENSIVE_GIFT_IDS),
                                references),
                        admission(floors, stage),
                        FloorReferences.check(floors, FloorReferenceData.of(gameDataRegistry)),
                        StartBuffRules.check(root.path(SELECTED_BUFF_IDS), season),
                        StartGiftRules.check(root.path(SELECTED_GIFT_KEYWORD), root.path(SELECTED_GIFT_IDS),
                                references, season))
                .flatMap(List::stream)
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private FloorBoundary.Parsed parseFloors(JsonNode root, MDCategory category) {
        return FloorBoundary.parse(root.path(FLOOR_SELECTIONS), gameDataRegistry.floorRules().layout(category));
    }

    private List<Violation> admission(FloorBoundary.Parsed floors, Stage stage) {
        return switch (floorRules.admit(floors, stage)) {
            case Admission.Admitted admitted -> admitted.boundaryViolations();
            case Admission.Rejected rejected -> rejected.violations();
        };
    }

    private static Violation unknownVersion(int version) {
        return new Violation(ErrorCode.UNKNOWN_VERSION, ContentPath.root(),
                String.format("No game data for content version %d", version));
    }

    private static PlannerDocument normalized(String content, JsonNode parsed, JsonNode root) {
        return root.equals(parsed) ? new PlannerDocument(content, false) : new PlannerDocument(root.toString(), true);
    }
}
