package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.sync.JsonDocuments;
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
import java.util.Optional;
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

    public String validate(String content, String category, int version, Stage stage) {
        return accepted(check(content, category, version, stage), content);
    }

    public String validateFloorRules(String content, String category, Stage stage) {
        return accepted(checkFloorRules(content, category, stage), content);
    }

    public boolean isSameDocument(String content, String stored) {
        return Optional.ofNullable(stored)
                .flatMap(storedContent -> ContentJson.parse(objectMapper, content).tree()
                        .flatMap(contentTree -> ContentJson.parse(objectMapper, storedContent).tree()
                                .map(storedTree -> JsonDocuments.sameDocument(contentTree, storedTree))))
                .orElse(false);
    }

    private static String accepted(ContentVerdict verdict, String content) {
        return switch (verdict) {
            case ContentVerdict.Accepted accepted -> accepted.content();
            case ContentVerdict.Refused refused -> {
                log.warn("Validation failed: {}", refused.violation().message());
                throw PlannerValidationException.refusing(refused, content);
            }
            case ContentVerdict.Rejected rejected -> {
                rejected.violations().forEach(violation ->
                        log.warn("Validation failed at {}: {}", violation.path().render(), violation.message()));
                throw PlannerValidationException.rejecting(rejected, content);
            }
        };
    }

    private ContentVerdict check(String content, String category, int version, Stage stage) {
        return DocumentChecks.emptiness(content)
                .or(() -> DocumentChecks.category(category))
                .<ContentVerdict>map(ContentVerdict.Refused::new)
                .orElseGet(() -> switch (ContentJson.parse(objectMapper, content)) {
                    case ContentJson.Malformed malformed ->
                            new ContentVerdict.Refused(DocumentChecks.malformed(malformed.detail()));
                    case ContentJson.Tree tree -> checkParsed(content, tree.root(), category, version, stage);
                });
    }

    private ContentVerdict checkParsed(String content, JsonNode parsed, String category, int version, Stage stage) {
        return document.contentSize(parsed)
                .or(() -> DocumentChecks.objectRoot(parsed))
                .<ContentVerdict>map(ContentVerdict.Refused::new)
                .orElseGet(() -> checkDocument(content, parsed, gameDataRegistry.idMigrations().normalize(parsed),
                        category, version, stage));
    }

    private ContentVerdict checkDocument(String content, JsonNode parsed, JsonNode root, String category,
                                         int version, Stage stage) {
        return DocumentChecks.unknownFields(root)
                .or(() -> DocumentChecks.requiredFields(root))
                .or(() -> document.noteSize(root))
                .<ContentVerdict>map(ContentVerdict.Refused::new)
                .orElseGet(() -> {
                    List<Violation> violations = contentViolations(root, category, version, stage);
                    return violations.isEmpty()
                            ? new ContentVerdict.Accepted(normalized(content, parsed, root))
                            : new ContentVerdict.Rejected(violations);
                });
    }

    private ContentVerdict checkFloorRules(String content, String category, Stage stage) {
        return DocumentChecks.category(category)
                .<ContentVerdict>map(ContentVerdict.Refused::new)
                .orElseGet(() -> switch (ContentJson.parse(objectMapper, content)) {
                    case ContentJson.Malformed malformed ->
                            new ContentVerdict.Refused(DocumentChecks.malformed(malformed.detail()));
                    case ContentJson.Tree tree -> checkFloors(content, tree.root(), category, stage);
                });
    }

    private ContentVerdict checkFloors(String content, JsonNode parsed, String category, Stage stage) {
        JsonNode root = gameDataRegistry.idMigrations().normalize(parsed);
        List<Violation> violations = admission(parseFloors(root, category), stage);
        return violations.isEmpty()
                ? new ContentVerdict.Accepted(normalized(content, parsed, root))
                : new ContentVerdict.Rejected(violations);
    }

    private List<Violation> contentViolations(JsonNode root, String category, int version, Stage stage) {
        SinnerKeys.Sinners equipment = SinnerKeys.parse(root.path(EQUIPMENT), ContentPath.of(EQUIPMENT));
        SinnerKeys.Sinners skillEAState = SinnerKeys.parse(root.path(SKILL_EA_STATE), ContentPath.of(SKILL_EA_STATE));
        FloorBoundary.Parsed floors = parseFloors(root, category);
        return Stream.of(
                        DocumentChecks.fieldKinds(root),
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
                        seasonal(root, version))
                .flatMap(List::stream)
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private FloorBoundary.Parsed parseFloors(JsonNode root, String category) {
        return FloorBoundary.parse(root.path(FLOOR_SELECTIONS),
                gameDataRegistry.floorRules().layout(MDCategory.fromValue(category)));
    }

    private List<Violation> admission(FloorBoundary.Parsed floors, Stage stage) {
        return switch (floorRules.admit(floors, stage)) {
            case Admission.Admitted admitted -> admitted.boundaryViolations();
            case Admission.Rejected rejected -> rejected.violations();
        };
    }

    private List<Violation> seasonal(JsonNode root, int version) {
        Optional<SeasonReferences> season = gameDataRegistry.hasSeason(version)
                ? Optional.of(new SeasonReferences(gameDataRegistry, version))
                : Optional.empty();
        return Stream.of(
                        season.isPresent() ? List.<Violation>of() : List.of(unknownVersion(version)),
                        StartBuffRules.check(root.path(SELECTED_BUFF_IDS), season.map(known -> known)),
                        StartGiftRules.check(root.path(SELECTED_GIFT_KEYWORD), root.path(SELECTED_GIFT_IDS),
                                references, season.map(known -> known)))
                .flatMap(List::stream)
                .toList();
    }

    private static Violation unknownVersion(int version) {
        return new Violation(ErrorCode.UNKNOWN_VERSION, ContentPath.root(),
                String.format("No game data for content version %d", version));
    }

    private static String normalized(String content, JsonNode parsed, JsonNode root) {
        return root.equals(parsed) ? content : root.toString();
    }
}
