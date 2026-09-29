package org.danteplanner.backend.planner.validation.identity;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

final class IdentityViolations {

    private static final String IDENTITY = "identity";

    private IdentityViolations() {
    }

    static Violation unknownIdentity(ContentPath path, String identityId) {
        return Violation.unknownId(ErrorCode.UNKNOWN_IDENTITY, path, IDENTITY, identityId);
    }

    static Violation identityOfAnotherSinner(ContentPath path, String sinnerKey, String identityId) {
        return new Violation(ErrorCode.MISMATCHED_IDENTITY, path,
                String.format("identity '%s' does not belong to sinner %s", identityId, sinnerKey));
    }

    static Violation outOfRange(ErrorCode code, ContentPath path, String field, long value, int min, int max) {
        return Violation.valueOutOfRange(code, path, field, String.valueOf(value), min, max);
    }
}
