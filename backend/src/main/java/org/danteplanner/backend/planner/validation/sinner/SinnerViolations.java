package org.danteplanner.backend.planner.validation.sinner;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

final class SinnerViolations {

    private static final String OBJECT = "object";
    private static final String INTEGER = "integer";

    private SinnerViolations() {
    }

    static Violation keyNotANumber(ContentPath containerPath, String key) {
        return Violation.wrongType(ErrorCode.INVALID_FIELD_TYPE, containerPath.key(key),
                String.format("%s key '%s'", containerPath.render(), key), INTEGER);
    }

    static Violation keyOutOfRange(ContentPath containerPath, String key, int sinner) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_SINNER, containerPath.key(key),
                String.format("%s key", containerPath.render()), String.valueOf(sinner),
                SinnerKeys.FIRST_SINNER, SinnerKeys.LAST_SINNER);
    }

    static Violation sinnerNotAnObject(ContentPath path) {
        return Violation.wrongType(ErrorCode.INVALID_FIELD_TYPE, path, path.render(), OBJECT);
    }

    static Violation deploymentOutOfRange(ContentPath path, int sinnerIndex) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, path, String.valueOf(sinnerIndex),
                DeploymentOrder.FIRST_SINNER_INDEX, DeploymentOrder.LAST_SINNER_INDEX);
    }
}
