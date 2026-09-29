package org.danteplanner.backend.planner.validation.ego;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.JsonKind;
import org.danteplanner.backend.planner.validation.JsonNumbers;
import org.danteplanner.backend.planner.validation.SinnerEntry;
import org.danteplanner.backend.planner.validation.Violation;
import org.danteplanner.backend.shared.util.GameConstants;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class EgoRules {

    private record EgoSlot(String sinnerKey, String egoType, JsonNode ego, ContentPath path) {

        boolean required() {
            return REQUIRED_EGO_TYPE.equals(egoType);
        }
    }

    private static final Set<String> EGO_TYPES = Set.of("ZAYIN", "TETH", "HE", "WAW", "ALEPH");

    private static final String REQUIRED_EGO_TYPE = "ZAYIN";
    private static final String EGOS = "egos";
    private static final String ID = "id";
    private static final String THREADSPIN = "threadspin";

    private EgoRules() {
    }

    public static List<Violation> check(List<SinnerEntry> equipment, EgoReferenceData data) {
        return equipment.stream()
                .flatMap(sinner -> egos(sinner, data))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> egos(SinnerEntry sinner, EgoReferenceData data) {
        JsonNode egos = sinner.value().path(EGOS);
        ContentPath path = sinner.path().field(EGOS);
        return switch (egos.getNodeType()) {
            case OBJECT -> Stream.concat(
                    requiredEgo(egos.path(REQUIRED_EGO_TYPE), path.field(REQUIRED_EGO_TYPE)),
                    egos.properties().stream()
                            .map(ego -> new EgoSlot(sinner.sinnerKey(), ego.getKey(), ego.getValue(),
                                    path.field(ego.getKey())))
                            .flatMap(slot -> egoSlot(slot, data)));
            case MISSING, NULL -> Stream.of(Violation.missing(ErrorCode.MISSING_EGO_ZAYIN, path));
            case STRING -> Stream.of(egos.asText().isBlank()
                    ? Violation.missing(ErrorCode.MISSING_EGO_ZAYIN, path)
                    : EgoViolations.egosNotAnObject(path));
            default -> Stream.of(EgoViolations.egosNotAnObject(path));
        };
    }

    private static Stream<Violation> requiredEgo(JsonNode ego, ContentPath path) {
        return switch (ego.getNodeType()) {
            case OBJECT -> Stream.empty();
            case MISSING, NULL -> Stream.of(EgoViolations.missingRequiredEgo(path));
            case STRING -> Stream.of(ego.asText().isBlank()
                    ? EgoViolations.missingRequiredEgo(path)
                    : Violation.invalidFieldType(path, JsonKind.OBJECT, ego));
            default -> Stream.of(Violation.invalidFieldType(path, JsonKind.OBJECT, ego));
        };
    }

    private static Stream<Violation> egoSlot(EgoSlot slot, EgoReferenceData data) {
        Stream<Violation> type = EGO_TYPES.contains(slot.egoType())
                ? Stream.empty()
                : Stream.of(EgoViolations.unknownEgoType(slot.path()));
        return Stream.concat(type, switch (slot.ego().getNodeType()) {
            case OBJECT -> egoId(slot, data);
            default -> Stream.empty();
        });
    }

    private static Stream<Violation> egoId(EgoSlot slot, EgoReferenceData data) {
        JsonNode id = slot.ego().path(ID);
        ContentPath idPath = slot.path().field(ID);
        return switch (id.getNodeType()) {
            case STRING -> id.asText().isBlank() && slot.required()
                    ? Stream.of(Violation.missing(ErrorCode.MISSING_EGO_ZAYIN, idPath))
                    : references(slot, id.asText(), data);
            case MISSING, NULL -> slot.required()
                    ? Stream.of(Violation.missing(ErrorCode.MISSING_EGO_ZAYIN, idPath))
                    : Stream.empty();
            default -> Stream.of(Violation.invalidFieldType(idPath, JsonKind.STRING, id));
        };
    }

    private static Stream<Violation> references(EgoSlot slot, String egoId, EgoReferenceData data) {
        ContentPath idPath = slot.path().field(ID);
        return !data.hasEgo(egoId)
                ? Stream.of(EgoViolations.unknownEgo(idPath, egoId))
                : !data.belongsToSinner(slot.sinnerKey(), egoId)
                        ? Stream.of(EgoViolations.egoOfAnotherSinner(idPath, slot.sinnerKey(), egoId))
                        : threadspin(slot, egoId, data);
    }

    private static Stream<Violation> threadspin(EgoSlot slot, String egoId, EgoReferenceData data) {
        JsonNode threadspin = slot.ego().path(THREADSPIN);
        ContentPath path = slot.path().field(THREADSPIN);
        return switch (threadspin.getNodeType()) {
            case MISSING -> Stream.empty();
            default -> JsonNumbers.intValue(threadspin)
                    .map(value -> threadspinValue(path, egoId, value, data))
                    .orElseGet(() -> Stream.of(Violation.invalidFieldType(path, JsonKind.NUMBER, threadspin)));
        };
    }

    private static Stream<Violation> threadspinValue(ContentPath path, String egoId, int threadspin,
                                                     EgoReferenceData data) {
        return threadspin < GameConstants.MIN_THREADSPIN || threadspin > GameConstants.MAX_THREADSPIN
                ? Stream.of(EgoViolations.threadspinOutOfRange(path, threadspin))
                : data.maxThreadspin(egoId)
                        .map(ceiling -> threadspin > ceiling
                                ? Stream.of(EgoViolations.threadspinAboveCeiling(path, egoId, threadspin, ceiling))
                                : Stream.<Violation>empty())
                        .orElseGet(() -> Stream.of(EgoViolations.missingThreadspinCeiling(path, egoId)));
    }
}
