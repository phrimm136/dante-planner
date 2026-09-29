package org.danteplanner.backend.planner.validation.sinner;

import org.danteplanner.backend.planner.validation.ContentPath;
import org.danteplanner.backend.planner.validation.ErrorCode;
import org.danteplanner.backend.planner.validation.Violation;

final class SinnerViolations {

    private static final int NO_ENTRIES = 0;

    private SinnerViolations() {
    }

    static Violation deploymentOutOfRange(ContentPath path, long sinnerIndex) {
        return Violation.valueOutOfRange(ErrorCode.OUT_OF_RANGE_DEPLOYMENT, path, String.valueOf(sinnerIndex),
                DeploymentOrder.FIRST_SINNER_INDEX, DeploymentOrder.LAST_SINNER_INDEX);
    }

    static Violation tooManyDeployed(ContentPath path, int count) {
        return Violation.valueOutOfRange(ErrorCode.EXCESS_DEPLOYMENT, path, String.format("%s count", path.render()),
                String.valueOf(count), NO_ENTRIES, DeploymentOrder.MAX_DEPLOYED);
    }

    static Violation deployedTwice(ContentPath path, ContentPath deploymentOrder, long sinnerIndex) {
        return Violation.duplicateValue(ErrorCode.DUPLICATE_DEPLOYMENT, path, deploymentOrder.render(),
                String.valueOf(sinnerIndex));
    }
}
