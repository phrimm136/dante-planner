package org.danteplanner.backend.planner.validation.ego;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;
import org.danteplanner.backend.shared.util.GameConstants;

import java.util.List;

final class EgoViolations {

    private static final String EGO = "EGO";

    private EgoViolations() {
    }

    static Violation egosNotAnObject(ContentPath path) {
        return Violation.wrongType(ErrorCode.INVALID_FIELD_TYPE, path, path.render(), "object");
    }

    static Violation missingRequiredEgo(ContentPath path) {
        return Violation.missingFields(ErrorCode.MISSING_EGO_ZAYIN, path, List.of(path.render()));
    }

    static Violation unknownEgoType(ContentPath path) {
        return Violation.wrongType(ErrorCode.INVALID_EGO_TYPE, path, path.render(),
                "valid EGO type (ZAYIN/TETH/HE/WAW/ALEPH)");
    }

    static Violation unknownEgo(ContentPath path, String egoId) {
        return Violation.unknownId(ErrorCode.UNKNOWN_EGO, path, EGO, egoId);
    }

    static Violation egoOfAnotherSinner(ContentPath path, String sinnerKey, String egoId) {
        return new Violation(ErrorCode.MISMATCHED_EGO, path,
                String.format("EGO '%s' does not belong to sinner %s", egoId, sinnerKey));
    }

    static Violation threadspinOutOfRange(ContentPath path, int threadspin) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_EGO_THREADSPIN, path, "threadspin",
                String.valueOf(threadspin), GameConstants.MIN_THREADSPIN, GameConstants.MAX_THREADSPIN);
    }

    static Violation missingThreadspinCeiling(ContentPath path, String egoId) {
        return new Violation(ErrorCode.MISSING_EGO_THREADSPIN_CEILING, path,
                String.format("EGO '%s' has no threadspin ceiling in game data", egoId));
    }

    static Violation threadspinAboveCeiling(ContentPath path, String egoId, int threadspin, int ceiling) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_EGO_THREADSPIN, path,
                String.format("threadspin for EGO %s", egoId), String.valueOf(threadspin),
                GameConstants.MIN_THREADSPIN, ceiling);
    }
}
