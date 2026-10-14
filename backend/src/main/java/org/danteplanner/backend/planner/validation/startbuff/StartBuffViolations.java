package org.danteplanner.backend.planner.validation.startbuff;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

final class StartBuffViolations {

    private static final int NO_BUFFS = 0;

    private StartBuffViolations() {
    }

    static Violation tooMany(ContentPath path, int count, int maxCount) {
        return Violation.valueOutOfRange(ErrorCode.EXCESS_STARTBUFF, path, String.format("%s count", path.render()),
                String.valueOf(count), NO_BUFFS, maxCount);
    }

    static Violation unknownBuff(ContentPath path, ContentPath selectedBuffIds, long buffId) {
        return Violation.unknownId(ErrorCode.UNKNOWN_STARTBUFF, path, selectedBuffIds.render(), String.valueOf(buffId));
    }

    static Violation duplicateBaseId(ContentPath path, ContentPath selectedBuffIds, long baseId) {
        return Violation.duplicateValue(ErrorCode.DUPLICATE_STARTBUFF, path,
                String.format("%s base IDs", selectedBuffIds.render()), String.valueOf(baseId));
    }
}
