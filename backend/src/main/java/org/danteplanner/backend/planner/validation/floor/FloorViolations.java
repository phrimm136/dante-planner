package org.danteplanner.backend.planner.validation.floor;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Set;

final class FloorViolations {

    private FloorViolations() {
    }

    static Violation missingFloor(ContentPath path) {
        return new Violation(ErrorCode.INSUFFICIENT_FLOORS, path,
                String.format("%s must have a theme pack selected", path.render()));
    }

    static Violation missingThemePack(ContentPath path) {
        return new Violation(ErrorCode.MISSING_THEMEPACK, path,
                String.format("%s must have a theme pack selected", path.render()));
    }

    static Violation duplicateThemePack(ContentPath path, String themePackId, ContentPath firstFloor) {
        return new Violation(ErrorCode.DUPLICATE_THEMEPACK, path,
                String.format("%s repeats theme pack '%s' from %s", path.render(), themePackId, firstFloor.render()));
    }

    static Violation themePackAfterGap(ContentPath path, ContentPath previousFloor) {
        return new Violation(ErrorCode.OUT_OF_ORDER_THEMEPACK, path,
                String.format("Invalid sequence: %s requires themePackId in %s", path.render(), previousFloor.render()));
    }

    static Violation normalAfterHard(ContentPath path, ContentPath firstHard) {
        return new Violation(ErrorCode.OUT_OF_ORDER_DIFFICULTY, path,
                String.format("Invalid sequence: %s is NORMAL after HARD in %s", path.render(), firstHard.render()));
    }

    static Violation difficultyNotAllowed(ErrorCode code, ContentPath path, Difficulty difficulty,
                                          Set<Integer> allowed) {
        int min = Collections.min(allowed);
        int max = Collections.max(allowed);
        return switch (difficulty) {
            case Difficulty.Set(int value) -> Violation.valueOutOfRange(code, path, String.valueOf(value), min, max);
            case Difficulty.OutOfRange(BigInteger value) ->
                    Violation.valueOutOfRange(code, path, value.toString(), min, max);
            case Difficulty.Unset() -> new Violation(code, path,
                    String.format("%s is absent; expected a value in range [%d-%d]", path.render(), min, max));
        };
    }

    static Violation duplicateGift(ContentPath path, String giftId) {
        return Violation.duplicateValue(ErrorCode.DUPLICATE_EGOGIFT, path, giftId);
    }

    static Violation unknownThemePack(ContentPath path, String themePackId) {
        return Violation.unknownId(ErrorCode.UNKNOWN_THEMEPACK, path, themePackId);
    }

    static Violation unknownGift(ContentPath path, String giftId) {
        return Violation.unknownId(ErrorCode.UNKNOWN_EGOGIFT_FLOOR, path, giftId);
    }

    static Violation giftNotAffordable(ContentPath path, String giftId, String themePackId) {
        return new Violation(ErrorCode.UNAFFORDABLE_EGOGIFT, path,
                String.format("Gift '%s' is not affordable for theme pack '%s'", giftId, themePackId));
    }
}
