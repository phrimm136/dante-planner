package org.danteplanner.backend.planner.validation.startbuff;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

final class StartBuffViolations {

    private static final int NO_BUFFS = 0;

    private StartBuffViolations() {
    }

    static Violation tooMany(ContentPath path, int count) {
        return Violation.valueOutOfRange(ErrorCode.EXCESS_STARTBUFF, path, String.format("%s count", path.render()),
                String.valueOf(count), NO_BUFFS, StartBuffRules.MAX_START_BUFFS);
    }

    static Violation unknownBuff(ContentPath path, ContentPath selectedBuffIds, int buffId) {
        return Violation.unknownId(ErrorCode.UNKNOWN_STARTBUFF, path, selectedBuffIds.render(), String.valueOf(buffId));
    }

    static Violation baseIdOutOfRange(ContentPath path, int baseId) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_STARTBUFF, path,
                String.format("%s base ID", path.render()), String.valueOf(baseId),
                StartBuffRules.MIN_BASE_ID, StartBuffRules.MAX_BASE_ID);
    }

    static Violation duplicateBaseId(ContentPath path, ContentPath selectedBuffIds, int baseId) {
        return Violation.duplicateValue(ErrorCode.DUPLICATE_STARTBUFF, path,
                String.format("%s base IDs", selectedBuffIds.render()), String.valueOf(baseId));
    }
}
