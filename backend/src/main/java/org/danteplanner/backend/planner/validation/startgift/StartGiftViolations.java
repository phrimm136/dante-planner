package org.danteplanner.backend.planner.validation.startgift;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

final class StartGiftViolations {

    private StartGiftViolations() {
    }

    static Violation duplicateGift(ContentPath path, ContentPath selectedGiftIds, String giftId) {
        return Violation.duplicateValue(ErrorCode.DUPLICATE_STARTGIFT, path, selectedGiftIds.render(), giftId);
    }

    static Violation unknownGift(ContentPath path, ContentPath selectedGiftIds, String giftId) {
        return Violation.unknownId(ErrorCode.UNKNOWN_EGOGIFT, path, selectedGiftIds.render(), giftId);
    }

    static Violation unknownKeyword(ContentPath path, String keyword) {
        return Violation.unknownId(ErrorCode.UNKNOWN_STARTGIFT_KEYWORD, path, keyword);
    }

    static Violation missingKeyword(ContentPath selectedGiftIds) {
        return new Violation(ErrorCode.MISSING_STARTGIFT_KEYWORD, selectedGiftIds,
                String.format("%s requires a selectedGiftKeyword", selectedGiftIds.render()));
    }

    static Violation outsidePool(ContentPath path, ContentPath selectedGiftIds, String keyword, String giftId) {
        return new Violation(ErrorCode.MISMATCHED_STARTGIFT, path,
                String.format("start gift '%s' is not in the '%s' keyword pool", giftId, keyword));
    }
}
