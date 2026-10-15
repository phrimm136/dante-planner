package org.danteplanner.backend.planner.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.planner.entity.MDCategory;
import org.danteplanner.backend.planner.entity.Planner;
import org.danteplanner.backend.planner.exception.PlannerValidationException;
import org.danteplanner.backend.planner.gamedata.GameDataRegistry;
import org.danteplanner.backend.planner.gamedata.IdentityLevels;
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
import org.danteplanner.backend.planner.validation.skill.SkillRules;
import org.danteplanner.backend.planner.validation.startbuff.StartBuffRules;
import org.danteplanner.backend.planner.validation.startgift.StartGiftRules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
@Slf4j
public class PlannerContentValidator {

    private record Normalized(String content, JsonNode parsed, JsonNode root) {

        PlannerDocument document(PlannerContent tree) {
            return root.equals(parsed)
                    ? new PlannerDocument(content, false, tree)
                    : new PlannerDocument(root.toString(), true, tree);
        }
    }

    private final ObjectMapper objectMapper;
    private final DocumentChecks document;
    private final GameDataRegistry gameDataRegistry;
    private final RegistryReferences references;
    private final FloorRules floorRules;
    private final ContentSchema schema = ContentSchema.planner();

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
                .orElseGet(() -> checkShape(clipped(normalized(content, parsed), season), category,
                        tree -> document.noteSize(tree.sectionNotes()),
                        tree -> contentViolations(tree, season, stage)));
    }

    private ContentVerdict checkFloorRules(String content, MDCategory category, Stage stage) {
        return switch (ContentJson.parse(objectMapper, content)) {
            case ContentJson.Malformed malformed ->
                    new ContentVerdict.Refused(DocumentChecks.malformed(malformed.detail()));
            case ContentJson.Tree tree -> checkShape(normalized(content, tree.root()), category,
                    admitted -> Optional.empty(),
                    admitted -> admission(admitted.floorSelections().parsed(), stage));
        };
    }

    private ContentVerdict checkShape(Normalized normalized, MDCategory category,
                                      Function<PlannerContent, Optional<Violation>> refusal,
                                      Function<PlannerContent, List<Violation>> rules) {
        List<Violation> shape = schema.violations(normalized.root());
        if (!shape.isEmpty()) {
            return new ContentVerdict.Rejected(shape);
        }
        PlannerContent tree = ContentJson.bind(objectMapper, normalized.root(),
                gameDataRegistry.floorRules().layout(category));
        return refusal.apply(tree)
                .<ContentVerdict>map(ContentVerdict.Refused::new)
                .orElseGet(() -> {
                    List<Violation> violations = rules.apply(tree);
                    return violations.isEmpty()
                            ? new ContentVerdict.Accepted(normalized.document(tree))
                            : new ContentVerdict.Rejected(violations);
                });
    }

    private Normalized normalized(String content, JsonNode parsed) {
        return new Normalized(content, parsed, gameDataRegistry.idMigrations().normalize(parsed));
    }

    private static Normalized clipped(Normalized normalized, Season season) {
        return new Normalized(normalized.content(), normalized.parsed(),
                IdentityLevels.clip(normalized.root(), season.identityMaxLevel()));
    }

    private List<Violation> contentViolations(PlannerContent tree, Season season, Stage stage) {
        FloorBoundary.Parsed floors = tree.floorSelections().parsed();
        return Stream.of(
                        DocumentChecks.selectedKeywords(tree.selectedKeywords(), stage),
                        IdentityRules.check(tree.identities(), references, season.identityMaxLevel()),
                        EgoRules.check(tree.egos(), references),
                        DeploymentOrder.check(tree.deploymentOrder()),
                        SkillRules.check(tree.skillEAState()),
                        EgoGiftRules.check(tree.observationGiftIds(), tree.comprehensiveGiftIds(), references),
                        admission(floors, stage),
                        FloorReferences.check(floors, FloorReferenceData.of(gameDataRegistry)),
                        StartBuffRules.check(tree.selectedBuffIds(), season),
                        StartGiftRules.check(Optional.ofNullable(tree.selectedGiftKeyword()), tree.selectedGiftIds(),
                                references, season))
                .flatMap(List::stream)
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
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
}
