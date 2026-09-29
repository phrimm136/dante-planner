package org.danteplanner.backend.planner.validation.identity;

import com.fasterxml.jackson.databind.JsonNode;
import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.JsonKind;
import org.danteplanner.backend.planner.validation.JsonNumbers;
import org.danteplanner.backend.planner.validation.SinnerEntry;
import org.danteplanner.backend.planner.validation.Violation;
import org.danteplanner.backend.shared.util.GameConstants;

import java.util.List;
import java.util.stream.Stream;

public final class IdentityRules {

    private record Bound(String field, ErrorCode code, int min, int max) {
    }

    private static final String IDENTITY = "identity";
    private static final String ID = "id";
    private static final Bound LEVEL = new Bound("level", ErrorCode.OUT_OF_RANGE_IDENTITY_LEVEL,
            GameConstants.MIN_LEVEL, GameConstants.MAX_LEVEL);
    private static final Bound UPTIE = new Bound("uptie", ErrorCode.OUT_OF_RANGE_IDENTITY_UPTIE,
            GameConstants.MIN_UPTIE, GameConstants.MAX_UPTIE);

    private IdentityRules() {
    }

    public static List<Violation> check(List<SinnerEntry> equipment, IdentityReferenceData data) {
        return equipment.stream()
                .flatMap(sinner -> identity(sinner, data))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> identity(SinnerEntry sinner, IdentityReferenceData data) {
        JsonNode identity = sinner.value().path(IDENTITY);
        ContentPath path = sinner.path().field(IDENTITY);
        return switch (identity.getNodeType()) {
            case OBJECT -> identityId(sinner.sinnerKey(), identity, path, data);
            case MISSING, NULL -> Stream.of(Violation.missing(ErrorCode.MISSING_IDENTITY, path));
            case STRING -> Stream.of(identity.asText().isBlank()
                    ? Violation.missing(ErrorCode.MISSING_IDENTITY, path)
                    : IdentityViolations.identityNotAnObject(path));
            default -> Stream.of(IdentityViolations.identityNotAnObject(path));
        };
    }

    private static Stream<Violation> identityId(String sinnerKey, JsonNode identity, ContentPath path,
                                                IdentityReferenceData data) {
        JsonNode id = identity.path(ID);
        ContentPath idPath = path.field(ID);
        return switch (id.getNodeType()) {
            case STRING -> id.asText().isBlank()
                    ? Stream.of(Violation.missing(ErrorCode.MISSING_IDENTITY, idPath))
                    : references(sinnerKey, id.asText(), identity, path, data);
            case MISSING, NULL -> Stream.of(Violation.missing(ErrorCode.MISSING_IDENTITY, idPath));
            default -> Stream.of(IdentityViolations.idNotAString(idPath));
        };
    }

    private static Stream<Violation> references(String sinnerKey, String identityId, JsonNode identity,
                                                ContentPath path, IdentityReferenceData data) {
        ContentPath idPath = path.field(ID);
        return !data.hasIdentity(identityId)
                ? Stream.of(IdentityViolations.unknownIdentity(idPath, identityId))
                : !data.belongsToSinner(sinnerKey, identityId)
                        ? Stream.of(IdentityViolations.identityOfAnotherSinner(idPath, sinnerKey, identityId))
                        : Stream.of(LEVEL, UPTIE).flatMap(bound -> bounded(identity, path, bound));
    }

    private static Stream<Violation> bounded(JsonNode identity, ContentPath path, Bound bound) {
        JsonNode node = identity.path(bound.field());
        ContentPath fieldPath = path.field(bound.field());
        return switch (node.getNodeType()) {
            case MISSING -> Stream.empty();
            default -> JsonNumbers.intValue(node)
                    .map(value -> value < bound.min() || value > bound.max()
                            ? Stream.of(IdentityViolations.outOfRange(bound.code(), fieldPath, bound.field(), value,
                                    bound.min(), bound.max()))
                            : Stream.<Violation>empty())
                    .orElseGet(() -> Stream.of(Violation.invalidFieldType(fieldPath, JsonKind.NUMBER, node)));
        };
    }
}
