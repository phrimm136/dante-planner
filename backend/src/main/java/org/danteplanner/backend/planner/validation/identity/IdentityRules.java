package org.danteplanner.backend.planner.validation.identity;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.danteplanner.backend.shared.util.GameConstants;

import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

public final class IdentityRules {

    private record Bound(String field, ToLongFunction<Identity> value, ErrorCode code, int min, int max) {
    }

    private static final ContentPath EQUIPMENT = ContentPath.of("equipment");
    private static final String IDENTITY = "identity";
    private static final String ID = "id";
    private static final Bound LEVEL = new Bound("level", Identity::level, ErrorCode.OUT_OF_RANGE_IDENTITY_LEVEL,
            GameConstants.MIN_LEVEL, GameConstants.MAX_LEVEL);
    private static final Bound UPTIE = new Bound("uptie", Identity::uptie, ErrorCode.OUT_OF_RANGE_IDENTITY_UPTIE,
            GameConstants.MIN_UPTIE, GameConstants.MAX_UPTIE);

    private IdentityRules() {
    }

    public static List<Violation> check(Map<String, Identity> identities, IdentityReferenceData data) {
        return identities.entrySet().stream()
                .flatMap(sinner -> identity(sinner.getKey(), sinner.getValue(), data))
                .sorted(Violation.BY_PATH_THEN_CODE)
                .toList();
    }

    private static Stream<Violation> identity(String sinnerKey, Identity identity, IdentityReferenceData data) {
        ContentPath path = EQUIPMENT.key(sinnerKey).field(IDENTITY);
        ContentPath idPath = path.field(ID);
        return !data.hasIdentity(identity.id())
                ? Stream.of(IdentityViolations.unknownIdentity(idPath, identity.id()))
                : !data.belongsToSinner(sinnerKey, identity.id())
                        ? Stream.of(IdentityViolations.identityOfAnotherSinner(idPath, sinnerKey, identity.id()))
                        : Stream.of(LEVEL, UPTIE).flatMap(bound -> bounded(identity, path, bound));
    }

    private static Stream<Violation> bounded(Identity identity, ContentPath path, Bound bound) {
        long value = bound.value().applyAsLong(identity);
        return value < bound.min() || value > bound.max()
                ? Stream.of(IdentityViolations.outOfRange(bound.code(), path.field(bound.field()), bound.field(), value,
                        bound.min(), bound.max()))
                : Stream.empty();
    }
}
